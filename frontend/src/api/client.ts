import axios from "axios";

/**
 * Shared HTTP client for the backend API.
 *
 * CSRF protection works out of the box: axios copies the XSRF-TOKEN cookie
 * into the X-XSRF-TOKEN header for same-origin requests.
 */
export const apiClient = axios.create({
    baseURL: "/api",
});
