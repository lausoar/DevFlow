"""引用溯源：分块偏移、来源字段与提示词编号约束的回归测试。"""
import pytest

from app.services.rag_service import RagService


class TestSplitWithOffsets:
    def test_offsets_point_back_to_original_text(self):
        text = "# 标题\n\n第一段内容。\n\n第二段内容更长一些。\n\n第三段。"
        splits = RagService()._split_with_offsets(text, max_tokens=500)
        assert splits, "应为非空分块"
        for chunk, start, end in splits:
            assert text[start:end] == chunk

    def test_offsets_handle_repeated_paragraphs(self):
        para = "重复段落" * 500  # 超过单块上限，强制独立分块
        text = f"{para}\n\n{para}\n\n独立段落。"
        splits = RagService()._split_with_offsets(text, max_tokens=100)
        starts = [start for _, start, _ in splits]
        assert len(starts) >= 2 and len(set(starts)) == len(starts), "重复段落不能复用同一偏移"
        for chunk, start, end in splits:
            assert text[start:end] == chunk

    def test_oversized_paragraph_splits_with_offsets(self):
        text = "长" * 5000
        splits = RagService()._split_with_offsets(text, max_tokens=100)
        assert len(splits) > 1
        for chunk, start, end in splits:
            assert text[start:end] == chunk
            assert len(chunk) <= 400

    def test_split_text_matches_offsets(self):
        text = "\n\n".join(f"段落{i}内容" for i in range(5))
        service = RagService()
        assert service._split_text(text) == [c for c, _, _ in service._split_with_offsets(text)]

    def test_blank_text_has_no_chunks(self):
        assert RagService()._split_with_offsets("   \n\n  ", max_tokens=500) == []


class TestCitationExcerpt:
    def test_short_content_is_unchanged(self):
        assert RagService._citation_excerpt("短文本") == "短文本"

    def test_long_content_is_truncated_with_ellipsis(self):
        excerpt = RagService._citation_excerpt("字" * 800, limit=500)
        assert excerpt.endswith("…")
        assert len(excerpt) == 501

    def test_empty_content_returns_empty_string(self):
        assert RagService._citation_excerpt("") == ""


@pytest.mark.parametrize("version,expected", [(3, 3), (None, 0), (0, 0)])
def test_document_version_defaults(version, expected):
    """索引请求缺版本时写 0，前端据此知道无法判断改版。"""
    assert int(version or 0) == expected
