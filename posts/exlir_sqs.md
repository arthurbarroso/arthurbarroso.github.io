Author: Arthur Barroso
Title: Handling message errors using Broadway and AWS SQS
Link: dealing with corrupt messages using broadway and sqs
Description: A brief post talking about data ingestion and error handling using Broadway and SQS queues
Date: 2021-11-22
Tags: elixir, broadway, sqs, aws, data

For the past few months, I've been working on an Elixir project for a fintech. This project had one particularity: its main source of data would come from SQS queues -almost no data would be created from the application itself. Since SQS needed to be used, Broadway seemed like the right tool for the job.

In this post, I am going to talk a little about how I managed to set up a data ingestion pipeline using Broadway to consume data from an SQS queue while dealing with corrupt messages and other constraints. I'll use simpler schemas and data to represent stuff, but it should help you in complex cases, such as the one I went through.

The project needed to be able to turn the data that came from the SQS queue into its Ecto schemas and insert those into the database. This meant that it was needed to do some data transformation with the message's contents.

## Table of contents:
1. [Base concerns](#concerns)
2. [Base application setup](#base)
3. [Dealing with corrupt messages](#corrupt)
4. [Messages and relationships](#relationships)

### Base concerns <a name="concerns"></a>

I had two main concerns while thinking about how to properly set up my data ingestion pipeline:
- Dealing with corrupt messages
- Dealing with message's relationships

Dealing with the possibility of having corrupt messages in the pipeline meant I had to do two things:
- Parse the message's JSON data into an elixir data structure
- Check whether this data structure conforms to a pre-defined schema or struct. I used Ecto's embedded schemas for this.

A constraint of this project was that it had many relationships in between its schemas and it was **never** supposed to insert data that had missing relationships counterparts. e.g.: If a message references a user that doesn't yet exist in the database it shouldn't yet be inserted/ingested -it should go back to the queue. e.g.:
1. Message A is received and its content contains an order that belongs to a user X
2. The pipeline checks if user X is already in the application's database
3. If user X doesn't yet exist then message A should go back to the queue (and this should be repeated until user X exists or the message gets removed from the queue)
4. If user X exists then message A gets consumed/inserted and then gets acknowledged (removed from the queue)

### Base application setup <a name="base"></a>
We'll start by creating a Phoenix application and adding Broadway and BroadwaySQS to our dependencies.
```bash 
mix phx.new post --no-html --no-assets --no-dashboard --no-live --no-mailer
```

```elixir
# post/config/config.exs
config :post,
 generators: [binary_id: true],
 ecto_repos: [Post.Repo]
 
config :post, Post.Repo,
 migration_primary_key: [type: :binary_id],
 migration_foreign_key: [type: :binary_id]
```

```elixir
# mix.exs
  defp deps do
    [
      {:broadway, "~> 1.0"},
      {:broadway_sqs, "~> 0.7.0"},
      {:hackney, "~> 1.9"}
    ]
  end
```

Having that setup, it is now time to get a Postgres database up and running, create our migrations, and then create our schemas.
```bash
mix ecto.gen.migration add_users && mix ecto.gen.migration add_orders
```

For this post, let's assume the messages our application will consume can be of two kinds:
```json
// For the user:
{
 "external_id": "some_string",
 "name": "some_string"
}

// For an order:
{
 "description": "some_string",
 "cart_list": [
  {"price": "some_decimal"},
  {"price": "some_decimal"}
 ],
 "external_id": "some_string",
 "user_id": "some_external_id(string)"
}
```
Similarly, we'll assume our schemas like the following:
```
user {
  external_id: string,
  name: string
}

order {
  description: string,
  price: decimal,
  user_id: uuid
}
```

Users migration:
```elixir
defmodule Post.Repo.Migrations.AddUsers do
  use Ecto.Migration

  def change do
    create table(:users) do
      add :name, :string, null: false
      add :external_id, :string, null: false, unique: true
      timestamps()
    end
  end
end
```

```elixir
defmodule Post.Repo.Migrations.AddOrders do
  use Ecto.Migration

  def change do
    create table(:orders) do
      add :description, :string, null: false
      add :price, :decimal, null: false
      add :external_id, :string, null: false, unique: true
      add :user_id, references(:users)
      timestamps()
    end
  end
end
```
With the migrations configured, lets run `mix ecto.setup` and create the schemas
```elixir
defmodule Post.User do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @fields [:name, :external_id]

  schema "users" do
    field :name, :string
    field :external_id, :string
  end

  def changeset(params) do
    %__MODULE__{}
    |> cast(params, @fields)
    |> unique_constraint([:external_id])
  end
end
```

```elixir
defmodule Post.Order do
  use Ecto.Schema
  import Ecto.Changeset

  alias Post.User

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  @fields [:description, :price, :external_id, :user_id]

  schema "orders" do
    field :description, :string
    field :price, :decimal
    field :external_id, :string

    timestamps()
    belongs_to :user, User
  end

  def changeset(params) do
    %__MODULE__{}
    |> cast(params, @fields)
    |> unique_constraint([:external_id])
  end
end
```
With the schemas set up, it is time to move on.

### Dealing with corrupt messages <a name="corrupt"></a>
The first step to get Broadway and SQS up and running is to install its dependencies and configure the necessary keys. I won't go into much detail on Broadway's installation since this is well covered by its documentation. Let's create our "Pipeline" (which is how I'll call our SQS message consuming functions/steps):
```elixir
defmodule Post.SQS.Broadway do
  use Broadway

  def start_link(_opts) do
    producer_module = Application.fetch_env!(:post, :sqs_producer)

    Broadway.start_link(__MODULE__,
      name: __MODULE__,
      producer: [
        module: producer_module
      ],
      processors: [
        default: [concurrency: 50]
      ],
      batchers: [
        default: [concurrency: 5, batch_size: 10, batch_timeout: 1000]
      ]
    )
  end

  def handle_message(_processor_name, message, _context) do
    message
  end

  def handle_batch(_batcher, messages, _batch_info, _config) do
    messages
  end

  def handle_failed(messages, _context) do
	messages
  end
end
```
This pipeline is pretty dummy as of now: it simply passes messages throughout the callbacks. Let's now tackle our first problem: validating data integrity.

We'll start by defining a module and a utility function for handling message errors and encode the message's JSON data (I decided to use Jason, which was already included in the project)
```elixir
defmodule Post.SQS.Handlers do
  alias Broadway.Message

  def handle_error(message, :invalid_json),
    do: Message.failed(message, Jason.encode!(%{type: :malformed_json}))

  def wrapped_decode(json) do
    case Jason.decode(json) do
      {:ok, data} -> {:ok, data}
      {:error, _r} -> {:error, :invalid_json}
    end
  end

  def verify(%{data: data} = message) do
    case wrapped_decode(data) do
      {:ok, map} -> Message.update_data(message, fn _ -> map end)
      {:error, message_reason} -> handle_error(message, message_reason)
    end
  end
end
```
The code above does two things:
- Wraps Jason.decode into a function that omits the decoding failure reason so we can get more consistent errors
- Tries to decode a message's data. If it succeeds, then the message's data gets updated, if not, then the message gets flagged as a failure with the failure type being "malformed_json"

The `handle_error` function is pretty useful: using it we're able to attach error reasons to our messages and then decide how we'll handle their errors on the `handle_failed` callback.

Now let's hook it to the pipeline and change the `handle_failed` callback:
```elixir
# Post.SQS.Broadway
  def handle_message(_processor_name, message, _context) do
    Handlers.verify(message)
  end
  
  def handle_failed(messages, _context) do
	  messages
  end

  def handle_failed(messages, _context) do
    statuses = Enum.map(messages, fn %{status: status} -> status end)
    IO.inspect(%{failed: "failed", statuses: statuses})

    messages
    |> Enum.map(fn m -> Broadway.Message.configure_ack(m, on_failed: :ack) end)
  end
```
Since messages that aren't possible to decode can't be used, we'll simply log those and acknowledge them, so we don't end up processing these more than once.

The fact that Jason can decode a message isn't yet a guarantee the data the application receives is the way it should be: there may be type mismatches between the application schema and the json data, for example. To handle this, we'll define two of Ecto's embedded schemas.
```elixir
defmodule Post.SQS.Fields do
  @doc """
  A module containing utility functions for transforming
  changesets into result tuples
  """

  defmacro __using__(_) do
    quote do
      use Ecto.Schema

      import Ecto.Changeset
      import Post.SQS.Fields
    end
  end

  def parse(
        %Ecto.Changeset{valid?: true, changes: changes},
        relationship_keys
      ) do
    mounted_relationships_data =
      relationship_keys
      |> Enum.reduce(changes, fn rlk, acc -> map_relationships_from_struct(rlk, acc) end)

    {:ok, mounted_relationships_data}
  end

  def parse(%Ecto.Changeset{valid?: false} = changeset, _relationship_keys),
    do: {:error, to_errors(changeset)}

  def parse(%Ecto.Changeset{
        valid?: true,
        changes: changes
      }),
      do: {:ok, changes}

  def parse(%Ecto.Changeset{valid?: false} = changeset),
    do: {:error, to_errors(changeset)}

  defp to_errors(changeset),
    do:
      Ecto.Changeset.traverse_errors(changeset, fn {message, opts} ->
        Regex.replace(~r"%{(\w+)}", message, fn _, key ->
          opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
        end)
      end)

  defp extract(data) when is_list(data), do: Enum.map(data, &Map.get(&1, :changes))
  defp extract(data) when is_map(data), do: Map.get(data, :changes)

  def map_relationships_from_struct(key, item) do
    relationship_items =
      item
      |> Map.get(key)
      |> extract()

    Map.put(item, key, relationship_items)
  end
end
```

```elixir
defmodule Post.SQS.User do
  use Post.SQS.Fields

  import Ecto.Changeset

  @fields [:external_id, :name]

  embedded_schema do
    field :external_id, :string
    field :name, :string
  end

  def changeset(params) do
    %__MODULE__{}
    |> cast(params, @fields)
    |> validate_required(@fields)
  end

  def to_result(user_changeset), do: parse(user_changeset)
end
```

```elixir
defmodule Post.SQS.OrderItem do
  use Post.SQS.Fields

  @fields [:price]

  embedded_schema do
    field :price, :decimal
  end

  def changeset(_order, params) do
    %__MODULE__{}
    |> cast(params, @fields)
    |> validate_required(@fields)
  end
end

defmodule Post.SQS.Order do
  use Post.SQS.Fields

  alias Post.SQS.OrderItem

  @fields [:description, :external_id, :user_id]

  embedded_schema do
    field :external_id, :string
    field :description, :string
    field :user_id, :string

    embeds_many :cart_list, OrderItem
  end

  def changeset(params) do
    %__MODULE__{}
    |> cast(params, @fields)
    |> validate_required(@fields)
    |> cast_embed(:cart_list, reqired: true)
  end

  def to_result(order_changeset), do: parse(order_changeset, [:cart_list])
end
```

In order to use the changeset's for data checking, we'll modify our `Handlers.verify` function

```elixir
  def handle_error(message, :unrecognized_data_structure),
    do: Message.failed(message,
          Jason.encode!(%{type: :unrecognized_data_structure}))

  def handle_error(message, :changeset_error),
    do: Message.failed(message, Jason.encode!(%{type: :changeset_error}))

  def verify_changeset(%{name: _} = data) do
    changeset = Post.SQS.User.changeset(data)
    case Post.SQS.User.to_result(changeset) do
      {:ok, data} -> {:ok, data}
      {:error, _reason} -> {:error, :changeset_error}
    end
  end

  def verify_changeset(%{description: _} = data) do
    changeset = Post.SQS.Order.changeset(data)
    case Post.SQS.Order.to_result(changeset) do
      {:ok, data} -> {:ok, data}
      {:error, _reason} -> {:error, :changeset_error}
    end
  end

  def verify_changeset(_data), do:
    {:error, :unrecognized_data}

  def verify(%{data: data} = message) do
    with {:ok, map} <- wrapped_decode(data),
         {:ok, verified_data} <- verify_changeset(map) do
      Message.update_data(message, fn _ -> verified_data end)
    else
      {:error, reason} ->
        handle_error(message, reason)
    end
  end
```
Our application is now ready to check whether messages are valid and conform to our specs. 
If we then feed SQS a corrupt message while our application is running we'll see two things happening:
- Our application logging the failed message
- The failed message being acknowledged (and therefore not being consumed again)

### Messages and relationships <a name="relationships"></a>
As previously stated, every Order in our application depends on the existence of a user with the order's `user_id`. Let's say we receive a message that contains an order with the user_id of `x123`, but there is no user with an `external_id` for this value in our database. How can we handle this situation?

There are two main ways we can tackle this issue:
- Inserting the order and later creating its relationships
- Sending back the order's message to the queue hoping a message for the user with `external_id: "x123"` gets into our queue before the order message "expires".

To be quite honest, I'd **always** pick the first option, but I couldn't. For several reasons, the application I was working on wasn't allowed to have "incomplete" data. Since I ended up going with the second option, I'll talk about how I did it.

To start, we'll create a new module, which will be responsible for checking our order messages' relationships:
```elixir
defmodule Post.SQS.Checker do
  import Ecto.Query

  alias Post.{Repo, User, SQS.Handlers}

  def get_user_by_external_id(%{user_id: user_external_id} = data) do
    case Repo.one(
          from(u in User, where: field(u, :external_id)
            == ^user_external_id, select: u)) do
      nil -> {:error, :missing_user}
      _user -> {:ok, data}
    end
  end

  def check_dependencies(%{user_id: _, description: _} = data), do:
    get_user_by_external_id(data)

  def check_dependencies(%{name: _, external_id: _} = data), do:
    {:ok, data}

  # There is no point in checking messages
  # that have failed the previous steps
  def check(%{status: {:failed, _reason}} = message), do: message

  def check(%{data: data, status: :ok} = message) do
    case check_dependencies(data) do
      {:ok, _data} -> message
      {:error, error} -> Handlers.handle_error(message, error)
    end
  end
end
```
We'll also need to make sure our `Handlers.handle_error` deals with the `missing_user` error type:
```elixir
  def handle_error(message, :invalid_json),
    do: Message.failed(message, Jason.encode!(%{type: :malformed_json}))
```

It is then time to hook our `check` function to our pipeline using the `handle_message` callback:
```elixir
  def handle_message(_processor_name, message, _context) do
    message
    |> Handlers.verify()
    |> Checker.check()
  end
```

Since we implemented all the error handling using the `Handlers.handle_error` function, we're now able to decide how to deal with the failed messages in `handle_failed` by checking the message's error message (which is a JSON containing the error type):
```elixir
  def extract_error_type(%{status: status} = message), do:
    %{message: message, error: Jason.decode!(status)}

  def configure_ack_based_on_error(
    %{error: %{type: :changeset_error}, message: m}), do:
    Broadway.Message.configure_ack(m, on_failed: :ack)

  def configure_ack_based_on_error(
    %{error: %{type: :invalid_json}, message: m}), do:
    Broadway.Message.configure_ack(m, on_failed: :ack)

  def configure_ack_based_on_error(
    %{error: %{type: :unrecognized_data_structure}, message: m}), do:
    Broadway.Message.configure_ack(m, on_failed: :ack)

  # When an user is missing we do not ackwnoledge the message
  # so it goes back to the queue :)
  def configure_ack_based_on_error(
    %{error: %{type: :missing_user}, message: m}), do:
    Broadway.Message.configure_ack(m, on_failed: :noop)

  def handle_failed(messages, _context) do
    messages
    |> Enum.map(&extract_error_type/1)
    |> Enum.map(&configure_ack_based_on_error/1)
  end
```
There are a few situations that will flag a message as corrupt:
- A message that can't be decoded `(invalid_json)`
- A message that doesn't conform to our embedded schemas `(changeset_error)`
- A message that doesn't pattern match our functions `(unrecognized_data_structure)`

Since corrupt messages can't be consumed we simply acknowledge them - it would also be wise to log these messages, which can be done using some of AWS's services or something like Sentry, AppSignal. 

When messages that depend on data that doesn't yet exist (`missing_user`, in this case) get through our pipeline, we simply send these back to the queue.

We would then be able to insert data into our database, but that is a point I wont cover since it is pretty straightforward.
