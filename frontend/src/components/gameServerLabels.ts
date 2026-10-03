import type { GameServerSource } from "../api/containers";

/** Explains why a container is (or is not) considered a game server. */
export const GAME_SERVER_SOURCE_LABELS: Record<GameServerSource, string> = {
    MANUAL: "classified manually",
    LABEL: "from the Docker label serverdashboard.gameserver",
    IMAGE: "detected by image",
    NONE: "not detected",
};
