import org.junit.Test;
import org.reactome.UniProtQuery;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 5/8/2023
 */
public class UniProtQueryIT {

    @Test
    public void mappingReturnsExpectedTargetIds() throws IOException, InterruptedException {
        final List<String> sourceIds = Arrays.asList("P21802", "P12345");

        final String targetDatabase = "KEGG";
        final List<String> expectedTargetIds = Arrays.asList();

        UniProtQuery uniProtQuery = UniProtQuery.getUniProtQuery();

        assertThat(
            uniProtQuery.getMapping(expectedTargetIds, targetDatabase).values(),
            hasItems(expectedTargetIds)
        );
    }

    @Test
    public void trEMBLIDReturnsTrueForIsTrEMBLID() {
        final String trEMBLAccessionId = "A0A024QZQ1";

        UniProtQuery uniProtQuery = UniProtQuery.getUniProtQuery();

        assertThat(
            uniProtQuery.isTrEMBLID(trEMBLAccessionId),
            is(equalTo(true))
        );
    }

    @Test
    public void nonTrEMBLIDReturnsFalseForIsTrEMBLID() {
        final String nonTrEMBLAccessionId = "P12345";

        UniProtQuery uniProtQuery = UniProtQuery.getUniProtQuery();

        assertThat(
            uniProtQuery.isTrEMBLID(nonTrEMBLAccessionId),
            is(equalTo(false))
        );
    }

    @Test
    public void fakeUniProtIDReturnsFalseForIsTrEMBLID() {
        final String fakeUniProtAccessionId = "R98765";

        UniProtQuery uniProtQuery = UniProtQuery.getUniProtQuery();

        assertThat(
            uniProtQuery.isTrEMBLID(fakeUniProtAccessionId),
            is(equalTo(false))
        );
    }

    @Test
    public void emptyUniProtIDReturnsFalseForIsTrEMBLID() {
        final String emptyUniProtAccessionId = "";

        UniProtQuery uniProtQuery = UniProtQuery.getUniProtQuery();

        assertThat(
            uniProtQuery.isTrEMBLID(emptyUniProtAccessionId),
            is(equalTo(false))
        );
    }

    @Test
    public void nonUniProtIDReturnsFalseForIsTrEMBLID() {
        final String nonUniProtAccessionId = "P1";

        UniProtQuery uniProtQuery = UniProtQuery.getUniProtQuery();

        assertThat(
            uniProtQuery.isTrEMBLID(nonUniProtAccessionId),
            is(equalTo(false))
        );
    }
}
