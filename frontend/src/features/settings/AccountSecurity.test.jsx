import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { auth } from '../../state/services';
import { mockFetch, reply } from '../../test/fakes';
import { AccountSecurity } from './AccountSecurity';

const account = (over = {}) => ({ id: 1, email: 'a@b.co', name: 'A', settings: {}, password_set: true, ...over });

describe('AccountSecurity', () => {
  it('asks for the current password', () => {
    auth.setState({ user: account() });
    render(<AccountSecurity />);
    expect(document.querySelector('input[autocomplete=current-password]')).not.toBeNull();
    expect(screen.getByRole('button', { name: 'Change password' })).toBeInTheDocument();
  });

  it("sends the change and shows the server's reason when refused", async () => {
    auth.setState({ user: account() });
    const calls = mockFetch(() => reply(400, { error: 'that password is too easy to guess' }));
    render(<AccountSecurity />);
    const user = userEvent.setup();
    await user.type(document.querySelector('input[autocomplete=current-password]'), 'old one');
    await user.type(document.querySelector('input[autocomplete=new-password]'), 'password1234');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(calls[0]).toMatchObject({ method: 'PUT', url: '/api/me/password', body: { current: 'old one', new: 'password1234' } });
    expect(await screen.findByRole('alert')).toHaveTextContent('too easy');
  });

  it('has no password form for an account made with Google or GitHub', () => {
    auth.setState({ user: account({ password_set: false }) });
    render(<AccountSecurity />);
    expect(screen.queryByRole('button', { name: 'Change password' })).toBeNull();
    expect(screen.getByText(/no password to change/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign out everywhere' })).toBeInTheDocument();
  });
});
