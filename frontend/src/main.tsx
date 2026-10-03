import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import {BrowserRouter} from "react-router-dom";
import {AuthProvider} from "./auth/AuthProvider";
import {MaintenanceProvider} from "./maintenance/MaintenanceProvider";

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
        <AuthProvider>
            <MaintenanceProvider>
                <App />
            </MaintenanceProvider>
        </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
