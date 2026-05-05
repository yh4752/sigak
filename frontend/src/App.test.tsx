// frontend/src/App.test.tsx
import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import App from './App'

it('renders the NavBar logo', async () => {
  render(<App />)
  expect(await screen.findByRole('link', { name: 'SIGAK' })).toBeInTheDocument()
})
