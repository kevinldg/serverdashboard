import type { GameServerStatus } from "../api/containers";
import { GAME_SERVER_SOURCE_LABELS } from "./gameServerLabels";

/** Small badge for game servers; renders nothing for other containers. */
export function GameServerBadge({ status }: { status: GameServerStatus }) {
    if (!status.gameServer) {
        return null;
    }
    return (
        <span
            className="inline-flex items-center rounded-full bg-game-soft px-2 py-0.5 text-xs font-medium text-game-fg ring-1 ring-inset ring-game-line"
            title={`Game server (${GAME_SERVER_SOURCE_LABELS[status.source]})`}
        >
            {status.profileName}
        </span>
    );
}
