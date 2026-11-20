import { describe, expect, it } from 'vitest';
import { escapeCsvCell, serializeCsv } from './csv';

describe('CSV formula neutralization', () => {
  it.each([
    ['=1+1', "'=1+1"],
    ['+SUM(A1:A2)', "'+SUM(A1:A2)"],
    ['-2+3', "'-2+3"],
    ['@SUM(A1)', "'@SUM(A1)"],
    ['\t=1+1', "'\t=1+1"],
    ['\tcmd', "'\tcmd"],
  ])('neutralizes dangerous prefix %j before quoting', (input, expected) => {
    expect(escapeCsvCell(input)).toBe(expected);
  });

  it('neutralizes CR/LF-prefixed variants while retaining the original control characters', () => {
    expect(escapeCsvCell('\r+1')).toBe('"\'\r+1"');
    expect(escapeCsvCell('\n-2+3')).toBe('"\'\n-2+3"');
    expect(escapeCsvCell('\r\n@SUM(A1)')).toBe('"\'\r\n@SUM(A1)"');
  });

  it('preserves numeric cells and ordinary negative-number strings', () => {
    expect(escapeCsvCell(-42)).toBe('-42');
    expect(escapeCsvCell(-3.14)).toBe('-3.14');
    expect(escapeCsvCell('-42')).toBe('-42');
    expect(escapeCsvCell('-.5')).toBe('-.5');
    expect(escapeCsvCell('-1e3')).toBe('-1e3');
    expect(escapeCsvCell(0)).toBe('0');
  });

  it('preserves BOM, Unicode, commas, quotes, newlines, and RFC4180 escaping', () => {
    const csv = serializeCsv(
      ['名称', '备注', '公式'],
      [
        ['你好, "世界"', '第一行\n第二行', '=1,2'],
        [-12.5, '普通', null],
      ],
    );

    expect(csv).toBe(
      '﻿名称,备注,公式\r\n'
      + '"你好, ""世界""","第一行\n第二行","\'=1,2"\r\n'
      + '-12.5,普通,',
    );
  });
});
