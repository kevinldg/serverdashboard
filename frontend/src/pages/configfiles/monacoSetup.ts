import { loader } from "@monaco-editor/react";
import * as monaco from "monaco-editor/editor/editor.api";
// Editor features (find, folding, context menu, diff editor, …)
import "monaco-editor/features/register.all";
// Only the languages configuration files need, instead of all ~80 bundled languages
import "monaco-editor/languages/definitions/ini/register";
import "monaco-editor/languages/definitions/shell/register";
import "monaco-editor/languages/definitions/xml/register";
import "monaco-editor/languages/definitions/yaml/register";
import "monaco-editor/language/json/monaco.contribution";
import EditorWorker from "monaco-editor/editor/editor.worker?worker";
import JsonWorker from "monaco-editor/language/json/json.worker?worker";

/**
 * Uses the bundled Monaco instead of loading it from a CDN (self-hosted application).
 * Imported only by the lazily loaded editor page, so Monaco does not slow down the rest of the app.
 */
self.MonacoEnvironment = {
    getWorker(_workerId: string, label: string) {
        return label === "json" ? new JsonWorker() : new EditorWorker();
    },
};

loader.config({ monaco });
