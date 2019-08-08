package nl.unimaas.ids.operations;

import java.io.IOException;

import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.slf4j.LoggerFactory;

/**
 * A class to upload to GraphDB SPARQL endpoint
 */
public class SparqlUpdate extends AbstractSparqlOperation {

	public SparqlUpdate(Repository repo, String varInputGraph, String varOutputGraph, String varServiceUrl) {
		super(repo, varInputGraph, varOutputGraph, varServiceUrl);
		logger = LoggerFactory.getLogger(SparqlUpdate.class.getName());
	}

	public void executeQuery(RepositoryConnection conn, String queryString, String outputFilepath) throws RepositoryException, MalformedQueryException, IOException {
		logger.info("Executing update query: " + queryString);
		
		// Query the SPARQL endpoint
		Update update = conn.prepareUpdate(QueryLanguage.SPARQL, queryString);
		update.execute();
	}
}
