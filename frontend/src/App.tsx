import {Navigate,Route,Routes} from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import DashboardPage from './pages/DashboardPage'
import ModulePage from './pages/ModulePage'
export default function App(){return <Routes><Route element={<AppLayout/>}><Route path="/" element={<DashboardPage/>}/><Route path="/:module" element={<ModulePage/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Route></Routes>}
