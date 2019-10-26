package nl.unimaas.ids.operations;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class to upload to GraphDB SPARQL endpoint
 */
public class Split {

	protected Logger logger = LoggerFactory.getLogger(Split.class.getName());
	
	private Repository repo;
	
	private String varOutputGraph;
	
	private int splitBufferSize;
	
	public Split(Repository repo, String varOutputGraph, int splitBufferSize) {
		this.repo = repo;
		this.varOutputGraph = varOutputGraph;
		this.splitBufferSize = splitBufferSize;
		System.out.println("Split buffer size: " + splitBufferSize);

		// With SPARQL executors
		// sparqlSelectExecutor =
		// SparqlOperationFactory.getSparqlExecutor(QueryOperation.select,
		// endpointUrl, username, password, variables);
		// sparqlUpdateExecutor =
		// SparqlOperationFactory.getSparqlExecutor(QueryOperation.update,
		// endpointUrl, username, password, variables);
	}
	
	public void executeSplitFromFile(String splitFile) throws IOException {
		
		logger.info("Split through file");

		File sFile = new File(splitFile);
		
		if(sFile.exists()) {
			logger.info("Reading split file");
			try (
		            Reader reader = Files.newBufferedReader(Paths.get(splitFile));
		            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withDelimiter('\t'));
		        ) {
		            for (CSVRecord csvRecord : csvParser) {
		                // Accessing Values by Column Index
		                String classToSplit = csvRecord.get(0);
		                String propertyToSplit = csvRecord.get(1);
		                String splitDelimiter = csvRecord.get(2);
	
		                this.executeSplit(classToSplit, propertyToSplit, splitDelimiter.charAt(0), '"', true);
		                
		            }
		        }
		}else {
			logger.info("Split file does not exist");
		}
        
	}

	public TupleQueryResult executeSplit(String classToSplit,
			String propertyToSplit, char splitDelimiter,
			char splitQuote, boolean deleteSplittedTriples) 
			throws RepositoryException,
			MalformedQueryException, IOException {
				
		String delim = String.valueOf(splitDelimiter);
				
		if(splitDelimiter == '|'){
			logger.info("escaping delimiter");
			//delim = "\\|"; // this is for Graph db
			delim = "|"; // this is for Virtuoso
		}
				
		String queryString = "SELECT ?s ?p ?toSplit ?g WHERE {"
				+ "    GRAPH ?g {" + "    	?s a <" + classToSplit + "> ;"
				+ "      ?p ?toSplit ." + "    	FILTER(?p = <"
				+ propertyToSplit + ">)." + "FILTER(regex(?toSplit, '"
				+ delim + "'))" + "    } }";

		System.out.println(queryString);
		System.out.println();

		RepositoryConnection conn = repo.getConnection();

		TupleQuery query = conn.prepareTupleQuery(queryString);
		TupleQueryResult selectResults = query.evaluate();

		ValueFactory f = repo.getValueFactory();

		// If graph not defined in params, then we use the graph from the
		// statement
		IRI graphIri = null;
		boolean graphFromParam = false;
		if (varOutputGraph != null) {
			graphFromParam = true;
			graphIri = f.createIRI(varOutputGraph);
		}

		ModelBuilder builder = new ModelBuilder();
		Model bulkUpdate = builder.build();

		int count = 0;
		int accum = 0;

		try {
			while (selectResults.hasNext()) {
				BindingSet bindingSet = selectResults.next();

				IRI subjectIri = f.createIRI(bindingSet.getValue("s").stringValue());
				IRI predicateIri = f.createIRI(bindingSet.getValue("p").stringValue());
				String stringToSplit = bindingSet.getValue("toSplit").stringValue();
				// Use graph IRI directly from the data, if no graph URI provided
				if (!graphFromParam) {
					graphIri = f.createIRI(bindingSet.getValue("g").stringValue());
				}
						
		    	String[] splitFragments = stringToSplit.split(delim+"(?=\")");

				
		    	for (String splitFragment: splitFragments) {          
			
						if(splitQuote != ' ') {
							splitFragment = splitFragment.replaceAll("^"+splitQuote+"|"+splitQuote+"$", "");				
						}
	
						bulkUpdate.add(subjectIri, predicateIri,
									f.createLiteral(splitFragment), graphIri);
						count++;
					
				} // for loop
				
				if ((count > splitBufferSize)) {
					conn.add(bulkUpdate, graphIri);
					bulkUpdate = builder.build();

					accum += count;
					System.out.println("Updated triples: " + accum);
					count = 0;

				} else if ((count <= splitBufferSize) && !selectResults.hasNext()) {
					conn.add(bulkUpdate, graphIri);
					accum += count;
					System.out.println("Total updated triples: " + accum);
				}
			} // while results
			// print the content of the cross references available in pharmgkb
			// Iterator it = availablePref.entrySet().iterator();
			// while (it.hasNext()) {
			// Map.Entry pair = (Map.Entry)it.next();
			// System.out.println(pair.getKey());
			// it.remove(); // avoids a ConcurrentModificationException
			// }

		} finally {
			selectResults.close();
			if (deleteSplittedTriples) {
				String deleteQueryString = "DELETE { " + "GRAPH ?g {"
						+ "?s ?p ?o." + "} " + "}WHERE {" + "GRAPH ?g {"
						+ "?s a <" + classToSplit + "> ;" + "?p ?o ."
						+ "FILTER(?p = <" + propertyToSplit + ">)."
						+ "FILTER(regex(?o, '" + splitDelimiter + "'))} } ";

				System.out.println();
				System.out.println(deleteQueryString);

				Update update = conn.prepareUpdate(deleteQueryString);
				update.execute();
			}
			conn.close();
		}
		return selectResults;
	}

}
