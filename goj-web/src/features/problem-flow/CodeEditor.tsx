import CodeMirror from "@uiw/react-codemirror";
import { java } from "@codemirror/lang-java";
import { python } from "@codemirror/lang-python";

interface CodeEditorProps {
  value: string;
  language: string;
  readOnly?: boolean;
  height?: string;
  onChange?: (value: string) => void;
}

function extensionFor(language: string) {
  if (language === "py3" || language.toLowerCase().includes("python")) return python();
  return java();
}

export function CodeEditor({
  value,
  language,
  readOnly = false,
  height = "430px",
  onChange,
}: CodeEditorProps) {
  return (
    <CodeMirror
      className={`goj-code-editor ${readOnly ? "is-readonly" : ""}`}
      value={value}
      height={height}
      extensions={[extensionFor(language)]}
      editable={!readOnly}
      readOnly={readOnly}
      basicSetup={{
        lineNumbers: true,
        foldGutter: true,
        highlightActiveLine: !readOnly,
        highlightActiveLineGutter: !readOnly,
      }}
      onChange={onChange}
    />
  );
}
