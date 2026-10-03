import { useCallback, useEffect, useRef, useState } from "react";

export interface ApiData<T> {
    data: T | null;
    /** Error of the last load attempt; previously loaded data is kept. */
    error: unknown;
    loading: boolean;
    /** Time of the last successful load. */
    lastUpdated: Date | null;
    reload: () => Promise<void>;
}

/**
 * Loads data once on mount; call {@link ApiData.reload} to refresh it manually.
 * To load different data (e.g. another ID), remount the component with a different `key`.
 */
export function useApiData<T>(load: () => Promise<T>): ApiData<T> {
    const [data, setData] = useState<T | null>(null);
    const [error, setError] = useState<unknown>(null);
    const [loading, setLoading] = useState(true);
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

    const loadRef = useRef(load);
    useEffect(() => {
        loadRef.current = load;
    });

    // State is only updated after the request, so this can run directly in an effect.
    const fetchData = useCallback(async () => {
        try {
            const result = await loadRef.current();
            setData(result);
            setError(null);
            setLastUpdated(new Date());
        } catch (loadError) {
            setError(loadError);
        } finally {
            setLoading(false);
        }
    }, []);

    const reload = useCallback(async () => {
        setLoading(true);
        await fetchData();
    }, [fetchData]);

    // Initial load; `loading` already starts as true.
    useEffect(() => {
        void fetchData();
    }, [fetchData]);

    return { data, error, loading, lastUpdated, reload };
}
