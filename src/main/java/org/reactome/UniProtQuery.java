package org.reactome;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 11/30/2022
 */
public class UniProtQuery {
    private final static String UNIPROT_REST_URL = "https://rest.uniprot.org/";

    /**
     * Get object for executing UniProt queries through their RESTful API
     * @return UniProt Query object
     */
    public static UniProtQuery getUniProtQuery() {
        return new UniProtQuery();
    }

    private UniProtQuery() {
        // Private constructor to force usage of static creation method
    }

    /**
     * Submit a list of UniProt accession ids and a target database to obtain a mapping of UniProt to
     * target database ids.
     *
     * @param uniprotIds List of UniProt accession ids to map
     * @param targetDatabase Name of target database to which to map
     *
     * @return Map of UniProt accession ids to list of target database ids
     *
     * @throws IOException Thrown if unable to submit, check status, or receive results for query
     * @throws InterruptedException Thrown if unable to wait while query completes
     */
    public Map<String, List<String>> getMapping(List<String> uniprotIds, String targetDatabase)
        throws IOException, InterruptedException {

        final long maximumWaitTimeInMinutes = 5;
        final long sleepDelayTimeInSeconds = 10;

        String jobId = submitQuery(uniprotIds, targetDatabase);
        long timeWaitedInMilliseconds = 0;
        while (true) {
            boolean jobFinished = jobFinished(jobId);
            if (jobFinished) {
                break;
            } else if (timeWaitedInMilliseconds < getMinutesToMilliseconds(maximumWaitTimeInMinutes)) {
                long sleepTimeInMilliseconds = getSecondsToMilliseconds(sleepDelayTimeInSeconds);
                Thread.sleep(sleepTimeInMilliseconds);
                timeWaitedInMilliseconds += sleepTimeInMilliseconds;
            } else {
                throw new RuntimeException("Waited " + maximumWaitTimeInMinutes + "minutes but job not finished for " +
                    uniprotIds.size() + " identifiers mapping to " + targetDatabase);
            }
        }

        return getJobResults(jobId);
    }

    /**
     * Queries UniProt for all TrEMBL accessions and writes them to the file at the provided path.
     *
     * @param fileToWriteTrEMBLIDs Path of file to write TrEMBL accessions
     * @throws IOException Thrown if unable to retrieve or write TrEMBL accessions
     */
    public void writeTrEMBLIDsToFile(Path fileToWriteTrEMBLIDs) throws IOException {
        Iterator<List<String>> tremblIDBatchIterator = TrEMBLQuery.getTrEMBLIDBatchIterator();
        while (tremblIDBatchIterator.hasNext()) {
            List<String> tremblIDBatch = tremblIDBatchIterator.next();
            for (String tremblID : tremblIDBatch) {
                Files.write(
                    fileToWriteTrEMBLIDs,
                    tremblID.concat(System.lineSeparator()).getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
                );
            }
        }
    }

    /**
     * Returns <code>true</code> if the UniProt Accession parameter is a TrEMBL ID in UniProt and <code>false</code>
     * otherwise.
     * @param uniProtAccession Accession to test if it is a TrEMBL ID accession
     * @return <code>true</code> if the parameter is a TrEMBL ID;<code>false</code> otherwise
     * @throws RuntimeException Thrown if unable to connect to the UniProt Server due to a response code of 5XX
     */
    public boolean isTrEMBLID(String uniProtAccession) {
        final String uniProtAccessionURLAsString = UNIPROT_REST_URL + "uniprotkb/" + uniProtAccession + ".txt";

        HttpURLConnection uniProtAccessionHttpURLConnection = null;
        try {
            URL uniProtAccessionURL = new URL(uniProtAccessionURLAsString);
            uniProtAccessionHttpURLConnection = (HttpURLConnection) uniProtAccessionURL.openConnection();
            uniProtAccessionHttpURLConnection.setRequestMethod("GET");
            BufferedReader uniProtAccessionReader = new BufferedReader(
                new InputStreamReader(uniProtAccessionHttpURLConnection.getInputStream()));

            return uniProtAccessionReader.lines().anyMatch(
                line -> line.matches("^.*(Unreviewed|TrEMBL).*$")
            );
        } catch (IOException e) {
            if (serverUnavailable(uniProtAccessionHttpURLConnection)) {
                throw new RuntimeException("Unable to connect to UniProt RESTful server ", e);
            } else {
                System.err.println("Unable to get content from " + uniProtAccessionURLAsString + ": " + e);
                return false;
            }
        }
    }

    private boolean serverUnavailable(HttpURLConnection uniProtAccessionHttpURLConnection) {
        try {
            return uniProtAccessionHttpURLConnection.getResponseCode() >= 500;
        } catch (IOException e) {
            return true;
        }
    }

    private String submitQuery(List<String> ids, String targetDatabase) throws IOException  {
        StringBuilder curlQueryBuilder = new StringBuilder();
        curlQueryBuilder.append("curl --request POST ");
        curlQueryBuilder.append(getUniProtMappingRestUrl() + "run ");
        curlQueryBuilder.append(getIdsInfoAsString(ids));
        curlQueryBuilder.append(getUniProtDatabaseInfoAsString());
        curlQueryBuilder.append(getTargetDatabaseInfoAsString(targetDatabase));

        Process process = Runtime.getRuntime().exec(curlQueryBuilder.toString());
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        return bufferedReader
            .lines()
            .filter(line -> line.contains("jobId"))
            .map(line -> {
                Matcher jobIdMatcher = Pattern.compile("\"jobId\":\"(.*)\"").matcher(line);
                if (jobIdMatcher.find()) {
                    return jobIdMatcher.group(1);
                } else {
                    throw new RuntimeException("Could not get job id from " + line);
                }
            })
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Curl could not get job id from query: " +
                curlQueryBuilder.toString()));
    }

    private static boolean jobFinished(String jobID) throws IOException {
        StringBuilder curlQueryBuilder = new StringBuilder();
        curlQueryBuilder.append("curl -s ");
        curlQueryBuilder.append(getUniProtMappingRestUrl() + "status/" + jobID);

        Process process = Runtime.getRuntime().exec(curlQueryBuilder.toString());
        BufferedReader jobStatusWebSource = new BufferedReader(new InputStreamReader(process.getInputStream()));


        return jobStatusWebSource
            .lines()
            .anyMatch(
                line -> line.contains("{\"jobStatus\":\"FINISHED\"}")
            );
    }

    private Map<String, List<String>> getJobResults(String jobID) throws IOException {
        String jobResultsURL = getUniProtMappingRestUrl() + "stream/" + jobID;

        BufferedReader jobResultsWebSource = getCurlReader("curl -s", jobResultsURL);

        Map<String, List<String>> uniProtIdToTargetIds = new HashMap<>();

        final Pattern mappingPairPattern = Pattern.compile("\"from\":\"(\\w+)\",\"to\":\"(.*?)\"");

        String resultsLine;
        while ((resultsLine = jobResultsWebSource.readLine()) != null) {
            Matcher mappingPairMatcher = mappingPairPattern.matcher(resultsLine);
            while (mappingPairMatcher.find()) {
                String uniprotId = mappingPairMatcher.group(1);
                String targetId = mappingPairMatcher.group(2);

                uniProtIdToTargetIds.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(targetId);
            }

        }

        return uniProtIdToTargetIds;
    }

    private static String getUniProtMappingRestUrl() {
        return getUniProtRestUrl() + "/idmapping/";
    }

    private static String getUniProtRestUrl() {
        return UNIPROT_REST_URL;
    }

    private String getIdsInfoAsString(List<String> ids) {
        return getInfoStringTaggerArguval() + "ids=" + "\"" + String.join( ",",ids) + "\"" + " ";
    }

    private String getUniProtDatabaseInfoAsString() {
        return getInfoStringTaggerArguval() + "from=" + "\"" + "UniProtKB_AC-ID" + "\"" + " ";
    }

    private String getTargetDatabaseInfoAsString(String targetDatabase) {
        return getInfoStringTaggerArguval() + "to=" + "\"" + targetDatabase + "\"" + " ";
    }

    private String getInfoStringTaggerArguval() {
        return "--form ";
    }

    private BufferedReader getCurlReader(String curlCommand, String url) throws IOException {
        Process process = Runtime.getRuntime().exec(curlCommand + " " + url);
        return new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    private long getMinutesToMilliseconds(long minutes) {
        final long minutesToSecondsConversionFactor = 60;

        long seconds = minutes * minutesToSecondsConversionFactor;

        return getSecondsToMilliseconds(seconds);
    }

    private long getSecondsToMilliseconds(long seconds) {
        final long secondsToMillisecondsConversionFactor = 1000;

        return seconds * secondsToMillisecondsConversionFactor;
    }
}
