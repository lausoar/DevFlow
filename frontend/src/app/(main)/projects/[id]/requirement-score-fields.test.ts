import { describe, expect, it } from 'vitest';
import { EMPTY_RICE, previewRiceScore, riceDraftOf, ricePayload } from './requirement-score-fields';

describe('previewRiceScore', () => {
  it('可能是空值时返回 null，避免把"未填写"当成 0 分', () => {
    expect(previewRiceScore(EMPTY_RICE)).toBeNull();
    expect(previewRiceScore({ reach: '100', impact: '1', confidence: '80', effort: '' })).toBeNull();
  });

  it('按 reach×impact×confidence÷effort 计算并保留一位小数', () => {
    expect(previewRiceScore({ reach: '500', impact: '2', confidence: '80', effort: '5' })).toBe(160);
    expect(previewRiceScore({ reach: '100', impact: '1', confidence: '50', effort: '3' })).toBe(16.7);
  });

  it('工作量为 0 或负数时没有分数', () => {
    expect(previewRiceScore({ reach: '10', impact: '1', confidence: '50', effort: '0' })).toBeNull();
    expect(previewRiceScore({ reach: '10', impact: '1', confidence: '50', effort: '-2' })).toBeNull();
  });
});

describe('ricePayload', () => {
  it('空串转成 undefined，不会把 0 发给后端', () => {
    expect(ricePayload(EMPTY_RICE)).toEqual({
      reach: undefined,
      impact: undefined,
      confidence: undefined,
      effort: undefined,
    });
  });

  it('填写值按数字下发', () => {
    expect(ricePayload({ reach: '300', impact: '0.5', confidence: '70', effort: '2.5' }))
      .toEqual({ reach: 300, impact: 0.5, confidence: 70, effort: 2.5 });
  });
});

describe('riceDraftOf', () => {
  it('未评分需求回填空表单', () => {
    expect(riceDraftOf(null)).toEqual(EMPTY_RICE);
    expect(riceDraftOf({ reach: null, impact: null, confidence: null, effort: null })).toEqual(EMPTY_RICE);
  });

  it('已评分需求回填现有输入', () => {
    expect(riceDraftOf({ reach: 100, impact: 1, confidence: 50, effort: 4 })).toEqual({
      reach: '100',
      impact: '1',
      confidence: '50',
      effort: '4',
    });
  });
});
