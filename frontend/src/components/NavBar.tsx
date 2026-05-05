import { Link } from 'react-router-dom'
import './NavBar.css'

export default function NavBar() {
  return (
    <nav className="navbar">
      <Link to="/" className="navbar__logo">SIGAK</Link>
      <span className="navbar__hint">AI · Security · Engineering</span>
    </nav>
  )
}
