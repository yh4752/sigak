// frontend/src/App.tsx
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import NavBar from './components/NavBar'
import HomePage from './pages/HomePage'
import ArticleDetailPage from './pages/ArticleDetailPage'

export default function App() {
  return (
    <BrowserRouter>
      <NavBar />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/articles/:id" element={<ArticleDetailPage />} />
      </Routes>
    </BrowserRouter>
  )
}
