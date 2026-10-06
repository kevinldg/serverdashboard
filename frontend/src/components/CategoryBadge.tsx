import type { CategoryInfo } from "../api/categories";
import { CATEGORY_COLORS } from "./categoryColors";

/** Badge in the category's color. */
export function CategoryBadge({ category }: { category: Pick<CategoryInfo, "name" | "color"> }) {
    return (
        <span
            className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${CATEGORY_COLORS[category.color].className}`}
            title="Category (classified manually)"
        >
            {category.name}
        </span>
    );
}
