package nl.unimaas.ids.operations;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.yaml.snakeyaml.Yaml;

public abstract class AbstractSparqlOperation implements SparqlQueryInterface {
	
	private SPARQLRepository repo;
	
	public AbstractSparqlOperation(String endpoint, String username, String password) {
		repo = new SPARQLRepository(endpoint);
		repo.setUsernameAndPassword(username, password);
		repo.initialize();
	}

	public void executeFiles(String filePath) throws Exception {
		
		try (RepositoryConnection conn = repo.getConnection()) {
			File inputFile = new File(filePath);
			if(!inputFile.exists())
				throw new IllegalArgumentException("Input file \"" + inputFile.getAbsolutePath() + "\" does not exist");
			if(!inputFile.canRead())
				throw new SecurityException("Can not read from input file \"" + inputFile.getAbsolutePath() + "\"");
			
			// TODO: if input file is yaml. then check how Alex was doing
			
			if (inputFile.isDirectory()) {
				Collection<File> files = FileUtils.listFiles(
						inputFile,
						new RegexFileFilter(".*\\.(rq|sparql)"),
						DirectoryFileFilter.DIRECTORY
				);
				// Recursively iterate over files in the directory
				Iterator<File> iterator = files.iterator();
				while (iterator.hasNext()) {
					File f = iterator.next();
					
					executeQuery(conn, FileUtils.readFileToString(f));
				}
				
			} else if (FilenameUtils.getExtension(inputFile.getPath()).equals("yaml")) { 
				parseYaml(conn, inputFile);
			} else {
				System.out.println("TODO: handle single files");
				//TODO: if single file provided 
				//conn.add(new File(filePath), null, Rio.getParserFormatForFileName(inputFile.getName()).get());
			}
			
		} catch (Exception e) {
			throw e;
		}

		//repo.shutDown();
	}

	@SuppressWarnings("unchecked")
	public void parseYaml(RepositoryConnection conn, File inputFile) throws Exception {

		Yaml yaml = new Yaml();
		Map<String, Object> yamlFile = (Map<String, Object>)yaml.load(new FileInputStream(inputFile));
		
		List<String> queries = (List<String>)yamlFile.get("queries");
		for(String queryString : queries) {
			executeQuery(conn, queryString);
		}		
	}
}
