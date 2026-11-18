/** Joins the truthy class names: cx('btn', active && 'on') → "btn on". */
export const cx = (...names) => names.filter(Boolean).join(' ');
