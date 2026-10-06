import type { CategoryColor } from "../api/categories";

/** Badge classes per category color (full class names, so Tailwind finds them). */
export const CATEGORY_COLORS: Record<CategoryColor, { label: string; className: string }> = {
    BLUE: { label: "Blue", className: "bg-cat-blue-soft text-cat-blue-fg ring-cat-blue-line" },
    GREEN: { label: "Green", className: "bg-cat-green-soft text-cat-green-fg ring-cat-green-line" },
    AMBER: { label: "Amber", className: "bg-cat-amber-soft text-cat-amber-fg ring-cat-amber-line" },
    RED: { label: "Red", className: "bg-cat-red-soft text-cat-red-fg ring-cat-red-line" },
    PURPLE: { label: "Purple", className: "bg-cat-purple-soft text-cat-purple-fg ring-cat-purple-line" },
    TEAL: { label: "Teal", className: "bg-cat-teal-soft text-cat-teal-fg ring-cat-teal-line" },
    GRAY: { label: "Gray", className: "bg-cat-gray-soft text-cat-gray-fg ring-cat-gray-line" },
};
