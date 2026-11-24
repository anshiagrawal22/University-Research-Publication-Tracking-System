import { Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import Home from './pages/Home';
import SubmitPublication from './pages/SubmitPublication';
import PublicationList from './pages/PublicationList';
import Dashboard from './pages/Dashboard';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/submit" element={<SubmitPublication />} />
        <Route path="/publications" element={<PublicationList />} />
        <Route path="/dashboard" element={<Dashboard />} />
      </Route>
    </Routes>
  );
}
