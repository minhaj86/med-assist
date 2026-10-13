import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SafetyDisclaimer } from './SafetyDisclaimer'

describe('SafetyDisclaimer', () => {
  it('shows the mandatory decision-support disclaimer', () => {
    render(<SafetyDisclaimer />)
    const note = screen.getByRole('note', { name: 'Clinical safety disclaimer' })
    expect(note).toHaveTextContent('AI-generated clinical decision support. Not a substitute for professional clinical judgment.')
    expect(note).toHaveTextContent('The clinician remains responsible for all medical decisions.')
  })
})
