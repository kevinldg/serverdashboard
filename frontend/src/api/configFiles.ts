import { apiClient } from "./client";

export type FileType = "FILE" | "DIRECTORY" | "SYMLINK" | "OTHER";

export interface FileInfo {
    path: string;
    name: string;
    type: FileType;
    size: number;
    modifiedAt: string;
    /** Regular text file with an allowed extension, at most 1 MB. */
    editable: boolean;
}

export interface ConfigFilesOverview {
    /** Directories where the file browser starts. */
    roots: string[];
    /** Known configuration files of the profile that exist. */
    knownFiles: FileInfo[];
    /** Browsing directories requires a running container. */
    running: boolean;
}

export interface FileContent {
    path: string;
    content: string;
    /** Identifies the loaded version; required for saving. */
    sha256: string;
    size: number;
    modifiedAt: string;
    hints: string[];
    /** The previous version, if the file was saved through the application before. */
    backup: { replacedAt: string; replacedBy: string } | null;
}

const base = (containerId: string) => `/containers/${encodeURIComponent(containerId)}/config-files`;

export async function getConfigFilesOverview(containerId: string): Promise<ConfigFilesOverview> {
    const response = await apiClient.get<ConfigFilesOverview>(base(containerId));
    return response.data;
}

export async function listDirectory(containerId: string, path: string): Promise<{ path: string; entries: FileInfo[] }> {
    const response = await apiClient.get<{ path: string; entries: FileInfo[] }>(`${base(containerId)}/directory`, { params: { path } });
    return response.data;
}

export async function readConfigFile(containerId: string, path: string): Promise<FileContent> {
    const response = await apiClient.get<FileContent>(`${base(containerId)}/content`, { params: { path } });
    return response.data;
}

export async function getConfigFileBackup(containerId: string, path: string) {
    const response = await apiClient.get<{ path: string; content: string; replacedAt: string; replacedBy: string }>(
        `${base(containerId)}/backup`, { params: { path } });
    return response.data;
}

/** Saves the file; fails with 409 if it changed since the version identified by expectedSha256 was loaded. */
export async function saveConfigFile(containerId: string, path: string, content: string, expectedSha256: string) {
    const response = await apiClient.put<{ path: string; sha256: string; savedAt: string }>(`${base(containerId)}/content`, {
        path,
        content,
        expectedSha256,
    });
    return response.data;
}

/** Monaco language for a file name. */
export function languageOf(path: string): string {
    const extension = path.slice(path.lastIndexOf(".") + 1).toLowerCase();
    switch (extension) {
        case "json":
            return "json";
        case "yml":
        case "yaml":
            return "yaml";
        case "xml":
            return "xml";
        case "sh":
            return "shell";
        case "properties":
        case "ini":
        case "cfg":
        case "conf":
        case "env":
        case "toml":
            return "ini";
        default:
            return "plaintext";
    }
}
