import { tags, styleTags, defaultHighlightStyle } from "@codemirror/highlight";
import { LezerLanguage } from "@codemirror/language";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { parser } from "lezer-clojure";

let theme = EditorView.theme({
  '.cm-content': { 'white-space': 'pre-wrap', padding: '10px 0', color: '#249292e'},
  '&.cm-focused': { outline: 'none' },
  '.cm-line': {
    padding: '0 9px',
    'line-height': '1.6',
    'font-size': '16px',
    'font-family': 'var(--code-font)'
  },
  '.cm-linenumber': {color: 'rgba(27, 31, 35, 0.3)'},
  '.cm-matchingBracket': { 'border-bottom': '1px solid var(--teal-color)', color: 'inherit' },
  '.cm-gutters': { background: '#fff', border: 'none' },
  '.cm-gutterElement': { 'margin-left': '5px' },
  '.cm-cursor': { visibility: 'hidden' },
  '&.cm-focused .cm-cursor': { visibility: 'visible' },
  '.cm-keyword': { color: '#d73a49'},
  '.cm-atom': { color: '#005cc5'},
  '.cm-number': { color: '#005cc5'},
  '.cm-def': { color: '#d73a49'},
  '.cm-variable': { color: '#24292e'},
  '.cm-variable-2': {color: '#d73a49'},
  '.cm-type': { color: '#6f42c1'},
  '.cm-property': { color: '#005cc5'},
  '.cm-comment': { color: '#6a737d'},
  '.cm-string': { color: '005cc5'},
  '.cm-string-2': { color: 'rgba(46,56,60,1)'},
  '.cm-meta': { color: '#24292e'},
  '.cm-qualifier': { color: '#6f42c1'},
  '.cm-builtin': { color: '#6f42c1'},
  '.cm-bracket': { color: '#24292e'},
  '.cm-tag': { color: '#22863a'},
  '.cm-attribute': { color: '#6f42c1'},
  '.cm-attribute': { color: '#032f62'},
  '.cm-string': { color: '032f62'},
  '.cm-hr': { color: '24292e'},
  '.cm-link': { color: '005cc5'},
  '.cm-error': { color: '#d73a49'},
  '.cm-invalidchar': { color: '#d73a49'}
});

let style = {
  DefLike: tags.keyword,
  "Operator/Symbol": tags.keyword,
  "VarName/Symbol": tags.definition(tags.variableName),
  Boolean: tags.atom,
  "DocString/...": tags.emphasis,
  "Discard!": tags.comment,
  Number: tags.number,
  StringContent: tags.string,
  Keyword: tags.atom,
  Nil: tags.null,
  LineComment: tags.lineComment,
  RegExp: tags.regexp,
  "\"\\\"\"": tags.string,
};

let cljParser = parser.configure({props: [styleTags(style)]});

let syntax = LezerLanguage.define({parser: cljParser}, {languageData: {commentTokens: {line: ";;"}}});
let extensions = [EditorView.editable.of(false), theme, defaultHighlightStyle, [syntax]];

document.querySelectorAll("code.clj").forEach( elt => {
  new EditorView({state: EditorState.create({doc: elt.innerText.trim(),
                                             extensions: [extensions]}),
                  parent: elt});
  elt.firstChild.remove();
});
