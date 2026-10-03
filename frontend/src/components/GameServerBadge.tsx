import type { GameServerStatus } from "../api/containers";
import { GAME_SERVER_SOURCE_LABELS } from "./gameServerLabels";

/** Small badge for game servers; renders nothing for other containers. */
export function GameServerBadge({ status }: { status: GameServerStatus }) {
    if (!status.gameServer) {
        return null;
    }
    return (
        <span
            className="inline-flex items-center rounded-full bg-violet-950 px-2 py-0.5 text-xs font-medium text-violet-300 ring-1 ring-inset ring-violet-800"
            title={`Game server (${GAME_SERVER_SOURCE_LABELS[status.source]})`}
        >
            {status.profileName}
        </span>
    );
}
