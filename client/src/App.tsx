import { Link, Route, Routes } from 'react-router'
import BookPage from './pages/BookPage.tsx'
import SearchPage from './pages/SearchPage.tsx'

// Deux pages, routées dans le navigateur. Toute nouvelle route doit aussi être
// déclarée côté serveur (engine.api.ClientRoutes) et dans nginx.conf.
function App() {
  return (
    <>
      <header className="top">
        <Link to="/" className="brand">Bibliothèque Gutenberg</Link>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<SearchPage />} />
          <Route path="/books/:id" element={<BookPage />} />
          <Route path="*" element={<p className="error">Page introuvable.</p>} />
        </Routes>
      </main>
    </>
  )
}

export default App
