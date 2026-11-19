import { Link, Route, Routes } from 'react-router'
import BookView from './BookView.tsx'
import SearchView from './SearchView.tsx'

// Deux vues, routées dans le navigateur. Toute nouvelle route doit aussi être
// déclarée côté serveur (engine.api.ClientRoutes) et dans nginx.conf.
function App() {
  return (
    <>
      <header className="top">
        <Link to="/" className="brand">Bibliothèque Gutenberg</Link>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<SearchView />} />
          <Route path="/books/:id" element={<BookView />} />
          <Route path="*" element={<p className="error">Page introuvable.</p>} />
        </Routes>
      </main>
    </>
  )
}

export default App
