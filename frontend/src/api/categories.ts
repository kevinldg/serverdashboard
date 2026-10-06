import { apiClient } from "./client";

/** Must match the backend validation (CategoryService). */
export const MAX_CATEGORY_NAME_LENGTH = 30;

export type CategoryColor = "BLUE" | "GREEN" | "AMBER" | "RED" | "PURPLE" | "TEAL" | "GRAY";

/** A container category as shown on containers, e.g. "System" or "Communication". */
export interface CategoryInfo {
    id: string;
    name: string;
    color: CategoryColor;
}

export interface ManagedCategory extends CategoryInfo {
    /** Number of containers the category is assigned to. */
    containerCount: number;
}

export interface CategoryInput {
    name: string;
    color: CategoryColor;
}

/** For the classification dialog. */
export async function listContainerCategories(): Promise<CategoryInfo[]> {
    const response = await apiClient.get<CategoryInfo[]>("/container-categories");
    return response.data;
}

export async function listCategories(): Promise<ManagedCategory[]> {
    const response = await apiClient.get<ManagedCategory[]>("/admin/categories");
    return response.data;
}

export async function createCategory(input: CategoryInput): Promise<ManagedCategory> {
    const response = await apiClient.post<ManagedCategory>("/admin/categories", input);
    return response.data;
}

export async function updateCategory(id: string, input: CategoryInput): Promise<ManagedCategory> {
    const response = await apiClient.put<ManagedCategory>(`/admin/categories/${encodeURIComponent(id)}`, input);
    return response.data;
}

/** Containers with this category fall back to automatic detection. */
export async function deleteCategory(id: string): Promise<void> {
    await apiClient.delete(`/admin/categories/${encodeURIComponent(id)}`);
}
