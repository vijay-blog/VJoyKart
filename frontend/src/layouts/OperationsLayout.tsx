import type { ReactNode } from 'react';
import { Icon } from '../components/Icons';

export type Screen = 'dashboard' | 'products' | 'categories' | 'orders';

const navigation: Array<{ id: Screen; label: string; icon: string }> = [
  { id: 'dashboard', label: 'Dashboard', icon: '▦' },
  { id: 'products', label: 'Products', icon: '□' },
  { id: 'categories', label: 'Categories', icon: '▱' },
  { id: 'orders', label: 'Orders', icon: '▤' },
];

interface OperationsLayoutProps {
  activeScreen: Screen;
  onNavigate: (screen: Screen) => void;
  children: ReactNode;
}

export function OperationsLayout({ activeScreen, onNavigate, children }: OperationsLayoutProps) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#dashboard" onClick={() => onNavigate('dashboard')}>
          <span className="brand-mark">V</span>
          <span>VJoyKart<small>OPERATIONS</small></span>
        </a>
        <nav aria-label="Main navigation">
          {navigation.map((item) => (
            <button className={activeScreen === item.id ? 'nav-item active' : 'nav-item'} key={item.id} onClick={() => onNavigate(item.id)} type="button">
              <Icon>{item.icon}</Icon>{item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-footer"><span className="live-dot" /> Platform connected</div>
      </aside>
      <main className="main-content">
        <header className="topbar">
          <div><p className="eyebrow">OPERATIONS CONSOLE</p><h1>{navigation.find((item) => item.id === activeScreen)?.label}</h1></div>
          <div className="admin-badge"><span>OA</span><div>Operations Admin<small>Administrator</small></div></div>
        </header>
        {children}
      </main>
    </div>
  );
}
