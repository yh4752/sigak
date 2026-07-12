import { Link } from 'react-router-dom'
import './NavBar.css'

export default function NavBar() {
  return (
    <nav className="navbar">
      <Link to="/" className="navbar__logo">SIGAK</Link>
      <div className="navbar__links" aria-label="Primary navigation">
        <Link to="/research" className="navbar__link">Research</Link>
        <span className="navbar__hint">AI · Security · Engineering</span>
      </div>
    </nav>
  )
}
