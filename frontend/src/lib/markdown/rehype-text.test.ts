import { describe, expect, it } from 'vitest';
import { rehypeCitations, rehypeHighlightFirst } from './rehype-text';

interface TextNode { type: 'text'; value: string }
interface ElementNode {
  type: 'element';
  tagName: string;
  properties?: Record<string, unknown>;
  children: Node[];
}
type Node = TextNode | ElementNode;
type Root = { type: 'root'; children: Node[] };

function paragraph(...children: Node[]): ElementNode {
  return { type: 'element', tagName: 'p', children };
}

function run(plugin: ReturnType<typeof rehypeCitations> | ReturnType<typeof rehypeHighlightFirst>, tree: Root) {
  plugin()(tree as never);
}

function texts(root: Root): string {
  return JSON.stringify(root);
}

describe('rehypeCitations', () => {
  it('wraps known [n] markers in a citation link', () => {
    const tree: Root = { type: 'root', children: [paragraph({ type: 'text', value: '结论来自资料 [1]。' })] };
    run(rehypeCitations({ 1: { href: '/docs?docId=d1', title: '接口文档' } }), tree);

    const p = tree.children[0] as ElementNode;
    const link = p.children.find((c) => c.type === 'element' && c.tagName === 'a') as ElementNode;
    expect(link).toBeDefined();
    expect(link.properties).toMatchObject({ href: '/docs?docId=d1', title: '接口文档' });
    expect(link.children[0]).toEqual({ type: 'text', value: '1' });
    expect(texts(tree)).not.toContain('[1]');
  });

  it('leaves unknown markers untouched so a hallucinated number is visible', () => {
    const tree: Root = { type: 'root', children: [paragraph({ type: 'text', value: '见 [7]' })] };
    run(rehypeCitations({ 1: { href: '/docs?docId=d1', title: 't' } }), tree);
    expect(texts(tree)).toContain('[7]');
    expect(texts(tree)).not.toContain('citation-marker');
  });

  it('keeps surrounding text and handles multiple markers in one node', () => {
    const tree: Root = {
      type: 'root',
      children: [paragraph({ type: 'text', value: 'A [1] B [2] C' })],
    };
    run(rehypeCitations({
      1: { href: '/a', title: 'a' },
      2: { href: '/b', title: 'b' },
    }), tree);

    const p = tree.children[0] as ElementNode;
    expect(p.children.map((c) => (c.type === 'text' ? c.value : c.tagName))).toEqual([
      'A ', 'a', ' B ', 'a', ' C',
    ]);
  });

  it('does nothing when there are no sources', () => {
    const tree: Root = { type: 'root', children: [paragraph({ type: 'text', value: '见 [1]' })] };
    run(rehypeCitations({}), tree);
    expect(texts(tree)).toContain('[1]');
  });
});

describe('rehypeHighlightFirst', () => {
  it('replaces the first occurrence with a mark element', () => {
    const tree: Root = {
      type: 'root',
      children: [
        paragraph({ type: 'text', value: '前缀 目标片段 后缀' }),
        paragraph({ type: 'text', value: '目标片段' }),
      ],
    };
    run(rehypeHighlightFirst('目标片段'), tree);

    const first = tree.children[0] as ElementNode;
    const mark = first.children.find((c) => c.type === 'element' && c.tagName === 'mark') as ElementNode;
    expect(mark).toBeDefined();
    expect(mark.children[0]).toEqual({ type: 'text', value: '目标片段' });
    const second = tree.children[1] as ElementNode;
    expect(second.children.every((c) => c.type === 'text')).toBe(true);
  });

  it('trims the needle and ignores blank input', () => {
    const tree: Root = { type: 'root', children: [paragraph({ type: 'text', value: 'abc' })] };
    run(rehypeHighlightFirst('  '), tree);
    expect(texts(tree)).not.toContain('mark');
    run(rehypeHighlightFirst(' abc '), tree);
    expect(texts(tree)).toContain('mark');
  });

  it('does not match inside nested elements twice', () => {
    const tree: Root = {
      type: 'root',
      children: [
        {
          type: 'element',
          tagName: 'div',
          children: [paragraph({ type: 'text', value: 'x 目标 y' })],
        },
      ],
    };
    run(rehypeHighlightFirst('目标'), tree);
    expect(texts(tree).match(/citation-highlight/g)).toHaveLength(1);
  });
});
