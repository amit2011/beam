package beam.gtfs;

import beam.agentsim.config.BeamConfig;
import com.typesafe.config.ConfigFactory;
import org.apache.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static beam.gtfs.SFBayPT2MATSim.CONFIG_FILE;

/**
 * Takes care of retrieving {@link Operator} data.
 *
 * Created by sfeygin on 11/11/16.
 */
public class OperatorDataUtility {

    private static final Logger log = Logger.getLogger(TransitDataDownloader.class);
    private String opMapPath;
    private String apiKey;
    private static final BeamConfig BEAM_CONFIG = BeamConfig.apply(ConfigFactory.parseFile(CONFIG_FILE).resolve());

    public OperatorDataUtility(){
        opMapPath= BEAM_CONFIG.beam().routing().gtfs().operatorsFile();
//        apiKey=BEAM_CONFIG.beam().routing().gtfs().apiKey();
    }


    public Map<String, String> getOperatorMap() {
        Map<String, String> operatorMap;
        if (new File(opMapPath).exists()){
            operatorMap = readOperatorMapFromFile(opMapPath);
        } else{
            log.info("Operator key file not found. Downloading and saving...");
            operatorMap = downloadOperatorMap(apiKey);
            saveOperatorMap(opMapPath, operatorMap);
        }
        return operatorMap;
    }


    private void saveOperatorMap(String opMapPath, Map<String,String> operatorMap) {
        try {
            CsvMapWriter csvMapWriter = new CsvMapWriter(IOUtils.getBufferedWriter(opMapPath), CsvPreference.STANDARD_PREFERENCE);
            final String[] opKeyArray = operatorMap.keySet().stream().toArray(String[]::new);
            csvMapWriter.writeHeader(opKeyArray);
            csvMapWriter.write(operatorMap, opKeyArray);
            csvMapWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info(String.format("Operator key file saved at %s", opMapPath));
    }

    private Map<String, String> readOperatorMapFromFile(String opMapPath) {
        CsvMapReader mapReader = new CsvMapReader(IOUtils.getBufferedReader(opMapPath), CsvPreference.STANDARD_PREFERENCE);
        final String[] header;
        Map<String, String> res = null;
        try {
            header = mapReader.getHeader(true);
            res = mapReader.read(header);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    private Map<String, String> downloadOperatorMap(String apiKey) {
        final TransitDataDownloader downloader = TransitDataDownloader.getInstance(apiKey);
        List<Operator> transitOperatorList = downloader.getTransitOperatorList();
        return transitOperatorList.stream().distinct().collect(Collectors.toMap(Operator::getName, Operator::getPrivateCode));
    }


}