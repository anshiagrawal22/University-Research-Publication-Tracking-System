import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';

const MENU_GAP = 6;
const VIEWPORT_PAD = 8;
const MAX_MENU_HEIGHT = 288;

/**
 * Styled replacement for <select> so the open list matches the rest of the UI
 * (native option lists are drawn by the OS and cannot be styled).
 * Follows the WAI-ARIA "select-only combobox" pattern: focus stays on the trigger,
 * the highlighted option is exposed through aria-activedescendant.
 *
 * options: [{ value: string, label: string }]
 * onChange receives the new value (string), not an event.
 */
export default function Select({
  value,
  onChange,
  options,
  placeholder = 'Select…',
  disabled = false,
  onBlur,
  id,
  className = '',
  'aria-invalid': ariaInvalid,
  'aria-label': ariaLabel,
}) {
  const autoId = useId();
  const baseId = id || `select-${autoId.replace(/:/g, '')}`;
  const listId = `${baseId}-list`;
  const triggerRef = useRef(null);
  const menuRef = useRef(null);
  const typeahead = useRef({ text: '', timer: null });
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [labelText, setLabelText] = useState(undefined);

  // Call sites wrap the control in <label>Text …</label>. A <label> does not name a
  // role="combobox" button for assistive tech, so reuse its leading text as aria-label.
  useLayoutEffect(() => {
    if (ariaLabel) return;
    const trigger = triggerRef.current;
    const label = trigger?.closest('label');
    if (!label) return;
    const first = Array.from(label.childNodes).find((node) => node !== trigger && node.textContent.trim());
    const text = first?.textContent.replace(/\*\s*$/, '').trim();
    if (text) setLabelText(text);
  }, [ariaLabel]);

  const selectedIndex = options.findIndex((option) => option.value === value);
  const selected = selectedIndex >= 0 ? options[selectedIndex] : null;

  const openMenu = useCallback((index) => {
    if (disabled || options.length === 0) return;
    setActiveIndex(index ?? (selectedIndex >= 0 ? selectedIndex : 0));
    setOpen(true);
  }, [disabled, options.length, selectedIndex]);

  const closeMenu = useCallback(() => {
    setOpen(false);
    setActiveIndex(-1);
  }, []);

  function choose(index) {
    const option = options[index];
    if (!option) return;
    if (option.value !== value) onChange(option.value);
    closeMenu();
    triggerRef.current?.focus();
  }

  // Place the menu under the trigger (or above it when there is no room), clamped to the viewport.
  const positionMenu = useCallback(() => {
    const trigger = triggerRef.current;
    const menu = menuRef.current;
    if (!trigger || !menu) return;
    const rect = trigger.getBoundingClientRect();
    menu.style.minWidth = `${rect.width}px`;
    menu.style.maxWidth = `${Math.max(rect.width, Math.min(360, window.innerWidth - VIEWPORT_PAD * 2))}px`;
    const spaceBelow = window.innerHeight - rect.bottom - MENU_GAP - VIEWPORT_PAD;
    const spaceAbove = rect.top - MENU_GAP - VIEWPORT_PAD;
    const natural = Math.min(menu.scrollHeight, MAX_MENU_HEIGHT);
    const placeAbove = spaceBelow < natural && spaceAbove > spaceBelow;
    const maxHeight = Math.max(120, Math.min(MAX_MENU_HEIGHT, placeAbove ? spaceAbove : spaceBelow));
    menu.style.maxHeight = `${maxHeight}px`;
    const height = Math.min(menu.scrollHeight, maxHeight);
    const top = placeAbove ? rect.top - MENU_GAP - height : rect.bottom + MENU_GAP;
    const width = menu.offsetWidth;
    const left = Math.min(Math.max(VIEWPORT_PAD, rect.left), window.innerWidth - width - VIEWPORT_PAD);
    menu.style.top = `${Math.round(top)}px`;
    menu.style.left = `${Math.round(left)}px`;
    menu.dataset.placement = placeAbove ? 'top' : 'bottom';
  }, []);

  useLayoutEffect(() => {
    if (!open) return undefined;
    positionMenu();
    window.addEventListener('resize', positionMenu);
    window.addEventListener('scroll', positionMenu, true);
    return () => {
      window.removeEventListener('resize', positionMenu);
      window.removeEventListener('scroll', positionMenu, true);
    };
  }, [open, positionMenu, options.length]);

  // Close when clicking anywhere outside the trigger and the menu.
  useEffect(() => {
    if (!open) return undefined;
    function handlePointer(event) {
      if (triggerRef.current?.contains(event.target) || menuRef.current?.contains(event.target)) return;
      closeMenu();
    }
    document.addEventListener('mousedown', handlePointer);
    document.addEventListener('touchstart', handlePointer);
    return () => {
      document.removeEventListener('mousedown', handlePointer);
      document.removeEventListener('touchstart', handlePointer);
    };
  }, [open, closeMenu]);

  // Keep the highlighted option visible while moving through a long list.
  // Scrolls only the menu itself (scrollIntoView would also scroll the page).
  useEffect(() => {
    const menu = menuRef.current;
    if (!open || activeIndex < 0 || !menu) return;
    const node = menu.children[activeIndex];
    if (!node) return;
    if (node.offsetTop < menu.scrollTop) {
      menu.scrollTop = node.offsetTop - 4;
    } else if (node.offsetTop + node.offsetHeight > menu.scrollTop + menu.clientHeight) {
      menu.scrollTop = node.offsetTop + node.offsetHeight - menu.clientHeight + 4;
    }
  }, [open, activeIndex]);

  useEffect(() => () => window.clearTimeout(typeahead.current.timer), []);

  function matchTypeahead(char) {
    const state = typeahead.current;
    window.clearTimeout(state.timer);
    state.text += char.toLowerCase();
    state.timer = window.setTimeout(() => { state.text = ''; }, 600);
    const start = open ? activeIndex : selectedIndex;
    const ordered = options.map((_, index) => (start + 1 + index) % options.length);
    const sameChar = state.text.split('').every((c) => c === state.text[0]);
    const needle = sameChar ? state.text[0] : state.text;
    return ordered.find((index) => options[index].label.toLowerCase().startsWith(needle)) ?? -1;
  }

  function handleKeyDown(event) {
    if (disabled) return;
    const last = options.length - 1;
    if (!open) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
        event.preventDefault();
        openMenu();
      } else if (event.key === 'Home' || event.key === 'End') {
        event.preventDefault();
        openMenu(event.key === 'Home' ? 0 : last);
      } else if (event.key.length === 1 && /\S/.test(event.key) && !event.ctrlKey && !event.metaKey && !event.altKey) {
        const match = matchTypeahead(event.key);
        if (match >= 0) openMenu(match);
      }
      return;
    }
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        setActiveIndex((index) => Math.min(last, index + 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (event.altKey) choose(activeIndex);
        else setActiveIndex((index) => Math.max(0, index - 1));
        break;
      case 'Home':
        event.preventDefault();
        setActiveIndex(0);
        break;
      case 'End':
        event.preventDefault();
        setActiveIndex(last);
        break;
      case 'PageDown':
        event.preventDefault();
        setActiveIndex((index) => Math.min(last, index + 8));
        break;
      case 'PageUp':
        event.preventDefault();
        setActiveIndex((index) => Math.max(0, index - 8));
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        choose(activeIndex);
        break;
      case 'Escape':
        event.preventDefault();
        closeMenu();
        break;
      case 'Tab':
        closeMenu();
        break;
      default:
        if (event.key.length === 1 && /\S/.test(event.key) && !event.ctrlKey && !event.metaKey && !event.altKey) {
          const match = matchTypeahead(event.key);
          if (match >= 0) setActiveIndex(match);
        }
    }
  }

  const triggerClass = [
    'select-trigger',
    open ? 'is-open' : '',
    selected ? '' : 'is-placeholder',
    ariaInvalid ? 'is-invalid' : '',
    className,
  ].filter(Boolean).join(' ');

  return (
    <>
      <button
        ref={triggerRef}
        id={baseId}
        type="button"
        role="combobox"
        className={triggerClass}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-activedescendant={open && activeIndex >= 0 ? `${baseId}-opt-${activeIndex}` : undefined}
        aria-invalid={ariaInvalid || undefined}
        aria-label={ariaLabel || labelText}
        disabled={disabled}
        onClick={() => (open ? closeMenu() : openMenu())}
        onKeyDown={handleKeyDown}
        onBlur={(event) => {
          if (menuRef.current?.contains(event.relatedTarget)) return;
          closeMenu();
          onBlur?.(event);
        }}
      >
        <span className="select-value">{selected ? selected.label : placeholder}</span>
        <ChevronDown className="select-chevron" size={16} strokeWidth={2} aria-hidden="true" />
      </button>
      {open && createPortal(
        <ul
          ref={menuRef}
          id={listId}
          role="listbox"
          className="select-menu"
          aria-labelledby={baseId}
          tabIndex={-1}
          style={{ top: -9999, left: -9999 }}
        >
          {options.map((option, index) => {
            const isSelected = index === selectedIndex;
            const isActive = index === activeIndex;
            return (
              <li
                key={`${option.value}-${index}`}
                id={`${baseId}-opt-${index}`}
                role="option"
                aria-selected={isSelected}
                className={['select-option', isActive ? 'is-active' : '', isSelected ? 'is-selected' : ''].filter(Boolean).join(' ')}
                onMouseDown={(event) => event.preventDefault()}
                onMouseMove={() => { if (!isActive) setActiveIndex(index); }}
                onClick={() => choose(index)}
              >
                <Check className="select-check" size={15} strokeWidth={2.2} aria-hidden="true" />
                <span>{option.label}</span>
              </li>
            );
          })}
        </ul>,
        document.body
      )}
    </>
  );
}
