import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Providers, workstationState } from '../../../test/render'
import { WorkstationContext } from '../WorkstationContext'
import { AlertCard, type AlertLike } from './AlertCards'

const ruleAlert: AlertLike = {
  origin: 'RULE', severity: 'CRITICAL', title: 'Severe hypoxia (demonstration rule)', supportingFactKeys: ['F4'], demonstrationOnly: true,
  sourceReference: 'demo.resp.hypoxia@1.0.0',
}
const aiAlert: AlertLike = {
  origin: 'AI', severity: 'URGENT', title: 'Possible time-critical cause', supportingFactKeys: ['F2'], evidenceKeys: ['K1'], demonstrationOnly: false,
  supportStatus: 'UNSUPPORTED',
}

const renderCard = (alert: AlertLike) => render(
  <Providers><WorkstationContext.Provider value={workstationState()}><AlertCard alert={alert} /></WorkstationContext.Provider></Providers>,
)

describe('AlertCard', () => {
  it('labels deterministic rule alerts with their origin and demonstration status', () => {
    renderCard(ruleAlert)
    const card = screen.getByRole('alert', { name: /Deterministic critical alert/ })
    expect(within(card).getByText('RULE')).toBeInTheDocument()
    expect(within(card).getByText('CRITICAL')).toBeInTheDocument()
    expect(within(card).getByText('DEMONSTRATION RULE')).toBeInTheDocument()
    expect(within(card).getByText('Source: demo.resp.hypoxia@1.0.0')).toBeInTheDocument()
    expect(within(card).queryByText(/AI-generated consideration/)).not.toBeInTheDocument()
  })

  it('renders AI red flags so they cannot be mistaken for rule warnings', () => {
    renderCard(aiAlert)
    const card = screen.getByRole('alert', { name: /AI-generated urgent alert/ })
    expect(within(card).getByText('AI-GENERATED')).toBeInTheDocument()
    expect(within(card).getByText('AI-generated consideration — verify against clinical findings')).toBeInTheDocument()
    expect(within(card).getByText('unsupported')).toBeInTheDocument()
    expect(within(card).queryByText('RULE')).not.toBeInTheDocument()
  })
})
