import { render, screen } from '@testing-library/react'
import App from './App'

test('the app starts and shows its name', () => {
  render(<App />)
  expect(screen.getByRole('heading', { name: 'Homeward' })).toBeInTheDocument()
})
