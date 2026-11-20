package com.devflow.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvUtilsTest {

    @Test
    void escapesNullAndOrdinaryText() {
        assertThat(CsvUtils.escape(null)).isEmpty();
        assertThat(CsvUtils.escape("plain text")).isEqualTo("plain text");
        assertThat(CsvUtils.escape("a,\"b\"")).isEqualTo("\"a,\"\"b\"\"\"");
    }

    @Test
    void neutralizesSpreadsheetFormulaPrefixes() {
        assertThat(CsvUtils.escape("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvUtils.escape("+1+1")).isEqualTo("'+1+1");
        assertThat(CsvUtils.escape("-1+1")).isEqualTo("'-1+1");
        assertThat(CsvUtils.escape("@SUM(A1:A2)")).isEqualTo("'@SUM(A1:A2)");
        assertThat(CsvUtils.escape("\t=1+1")).isEqualTo("'\t=1+1");
        assertThat(CsvUtils.escape("  =1+1")).isEqualTo("'  =1+1");
    }

    @Test
    void neutralizesBeforeDelimiterQuoting() {
        assertThat(CsvUtils.escape("=1+1,2")).isEqualTo("\"'=1+1,2\"");
    }
}
