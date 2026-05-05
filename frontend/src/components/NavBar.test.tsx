import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { expect, it } from 'vitest'
import NavBar from './NavBar'

it('renders the SIGAK logo as a link to home', () => {
  render(<MemoryRouter><NavBar /></MemoryRouter>)
  const logo = screen.getByRole('link', { name: 'SIGAK' })
  expect(logo).toBeInTheDocument()
  expect(logo).toHaveAttribute('href', '/')
})

it('renders the category hint text', () => {
  render(<MemoryRouter><NavBar /></MemoryRouter>)
  expect(screen.getByText('AI · Security · Engineering')).toBeInTheDocument()
})
