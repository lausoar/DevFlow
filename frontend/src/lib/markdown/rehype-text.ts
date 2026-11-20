/**
 * 轻量的 rehype 文本改写插件。
 *
 * 只处理文本节点（不改 HTML 结构），因此不会引入原始 HTML 执行风险：
 *  - highlight：把命中的片段包成 <mark>，用于"跳到原文"后定位来源段落；
 *  - citations：把答案里的 [n] 变成可点击的引用角标。
 */

interface HastText {
  type: 'text';
  value: string;
}

interface HastElement {
  type: 'element';
  tagName: string;
  properties?: Record<string, unknown>;
  children: HastNode[];
}

type HastNode = HastText | HastElement;

interface HastRoot {
  type: 'root';
  children: HastNode[];
}

function isText(node: HastNode): node is HastText {
  return node.type === 'text';
}

/** 在渲染后的 Markdown 文本节点里高亮指定片段（只替换首个命中，避免重复段落乱跳）。 */
export function rehypeHighlightFirst(text?: string | null) {
  const needle = (text ?? '').trim();
  return () => (tree: HastRoot) => {
    if (!needle) return;
    let done = false;
    const walk = (node: HastRoot | HastElement) => {
      if (done) return;
      for (const child of [...node.children]) {
        if (isText(child)) {
          const index = child.value.indexOf(needle);
          if (index < 0) continue;
          const at = node.children.indexOf(child);
          if (at < 0) continue;
          const before = child.value.slice(0, index);
          const after = child.value.slice(index + needle.length);
          const replacement: HastNode[] = [];
          if (before) replacement.push({ type: 'text', value: before });
          replacement.push({
            type: 'element',
            tagName: 'mark',
            properties: { className: ['citation-highlight'] },
            children: [{ type: 'text', value: needle }],
          });
          if (after) replacement.push({ type: 'text', value: after });
          node.children.splice(at, 1, ...replacement);
          done = true;
          return;
        }
        walk(child);
        if (done) return;
      }
    };
    walk(tree);
  };
}

export interface CitationLink {
  href: string;
  title: string;
}

/** 把答案文本里的 [n] 替换为指向来源的链接；n 不在来源表里时保持原样。 */
export function rehypeCitations(links: Record<number, CitationLink>) {
  return () => (tree: HastRoot) => {
    if (!links || Object.keys(links).length === 0) return;
    const pattern = /\[(\d+)\]/g;
    const walk = (node: HastRoot | HastElement) => {
      for (const child of [...node.children]) {
        if (isText(child)) {
          if (![...child.value.matchAll(pattern)].some((m) => links[Number(m[1])])) continue;
          const at = node.children.indexOf(child);
          if (at < 0) continue;
          const parts: HastNode[] = [];
          let last = 0;
          for (const found of child.value.matchAll(pattern)) {
            const index = found.index ?? 0;
            const link = links[Number(found[1])];
            if (!link) continue;
            if (index > last) parts.push({ type: 'text', value: child.value.slice(last, index) });
            parts.push({
              type: 'element',
              tagName: 'a',
              properties: {
                href: link.href,
                className: ['citation-marker'],
                title: link.title,
              },
              children: [{ type: 'text', value: found[1] }],
            });
            last = index + found[0].length;
          }
          if (last < child.value.length) {
            parts.push({ type: 'text', value: child.value.slice(last) });
          }
          node.children.splice(at, 1, ...parts);
        } else {
          walk(child);
        }
      }
    };
    walk(tree);
  };
}
