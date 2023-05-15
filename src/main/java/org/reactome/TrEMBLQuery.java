package org.reactome;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 4/27/2023
 */
class TrEMBLQuery {
    private static final int TREMBL_ID_BATCH_SIZE = 500;
    private static final String INITIAL_TREMBL_QUERY_URL =
        "https://rest.uniprot.org/uniprotkb/search?format=list&compressed=true&query=(reviewed%3Afalse)&size=" +
        TREMBL_ID_BATCH_SIZE;

    private TrEMBLQuery() {}

    /**
     * Obtains an iterator over each batch of TrEMBL ids retrieved from UniProt.
     *
     * @return Iterator over batches of TrEMBL ids (each batch as a List of Strings)
     */
    static Iterator<List<String>> getTrEMBLIDBatchIterator() {
        return new Iterator<List<String>>() {
            private TrEMBLQueryResult currentTrEMBLQueryResult;

            @Override
            public boolean hasNext() {
                if (currentTrEMBLQueryResult == null) {
                    return true;
                }

                String nextTrEMBLQueryURL = currentTrEMBLQueryResult.getNextTrEMBLQueryURL();
                return nextTrEMBLQueryURL != null && !nextTrEMBLQueryURL.isEmpty();
            }

            @Override
            public List<String> next() {
                try {
                    if (currentTrEMBLQueryResult == null) {
                        currentTrEMBLQueryResult = runQuery();
                    } else {
                        currentTrEMBLQueryResult = runQuery(
                            currentTrEMBLQueryResult.getNextTrEMBLQueryURL()
                        );
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Unable to query next batch of TrEMBL IDs", e);
                }
                return currentTrEMBLQueryResult.getTremblIdBatch();
            }

            private TrEMBLQueryResult runQuery() throws IOException {
                return runQuery(getInitialTrEMBLQueryURL());
            }

            private TrEMBLQueryResult runQuery(String nextQueryURL) throws IOException {
                HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(nextQueryURL).openConnection();
                httpURLConnection.setRequestMethod("GET");
                return new TrEMBLQueryResult(httpURLConnection);
            }
        };
    }

    private static String getInitialTrEMBLQueryURL() {
        return INITIAL_TREMBL_QUERY_URL;
    }

    private static class TrEMBLQueryResult {
        private String nextTrEMBLQueryURL;
        private List<String> tremblIdBatch;

        TrEMBLQueryResult(HttpURLConnection httpURLConnection) {
            this.nextTrEMBLQueryURL = getNextTrEMBLQueryURL(httpURLConnection);
            try {
                this.tremblIdBatch = getTrEMBLIdBatch(httpURLConnection);
            } catch (IOException e) {
                throw new RuntimeException("Unable to get TrEMBL ids from " + httpURLConnection, e);
            }
        }

        /**
         * Returns the URL for querying the next batch of TrEMBL accession ids from UniProt.
         *
         * @return URL for next batch of TrEMBL accession ids as a String
         */
        String getNextTrEMBLQueryURL() {
            return this.nextTrEMBLQueryURL;
        }

        /**
         * Returns the list of TrEMBL accession ids in the current batch.
         *
         * @return List of TrEMBL accession ids as Strings
         */
        List<String> getTremblIdBatch() {
            return this.tremblIdBatch;
        }

        /**
         * Returns a summary of the number of TrEMBL accession ids in the current batch and the URL to query the next
         * batch.
         *
         * @return Summary of number of TrEMBL accession ids in current batch and the URL for the next batch
         */
        @Override
        public String toString() {
            return String.format("%d ids in batch; Next query url is %s",
                getTremblIdBatch().size(),
                getNextTrEMBLQueryURL());
        }

        private String getNextTrEMBLQueryURL(HttpURLConnection httpURLConnection) {
            String urlHeaderValue = httpURLConnection.getHeaderField("link");

            Pattern nextTrEMBLQueryURLPattern = Pattern.compile("<(.*?)>");
            Matcher nextTrEMBLQueryURLMatcher = nextTrEMBLQueryURLPattern.matcher(urlHeaderValue);

            if (!nextTrEMBLQueryURLMatcher.find()) {
                throw new RuntimeException("Unable to match next TrEMBL Query URL from " + urlHeaderValue);
            }

            return nextTrEMBLQueryURLMatcher.group(1);
        }

        private List<String> getTrEMBLIdBatch(HttpURLConnection httpURLConnection) throws IOException {
            BufferedReader responseBufferedReader = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(httpURLConnection.getInputStream()))
            );

            return responseBufferedReader.lines().collect(Collectors.toList());
        }
    }
}
