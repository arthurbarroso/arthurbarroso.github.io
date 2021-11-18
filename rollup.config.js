import {nodeResolve} from "@rollup/plugin-node-resolve";
import { terser } from 'rollup-plugin-terser';

export default {
  input: "clojure_highlighter.js",
  output: {
    file: "docs/js/clojure_highlighter.js",
    format: "iife"
  },
  plugins: [nodeResolve(), terser()]
};
