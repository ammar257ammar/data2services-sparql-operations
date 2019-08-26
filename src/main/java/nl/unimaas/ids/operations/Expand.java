package nl.unimaas.ids.operations;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Expand {
	
protected Logger logger = LoggerFactory.getLogger(Expand.class.getName());
	
	private Repository repo;
	
	private String varOutputGraph;
	
	private int expandBufferSize;
	
	public Expand(Repository repo, String varOutputGraph, int expandBufferSize) {
		this.repo = repo;
		this.varOutputGraph = varOutputGraph;
		this.expandBufferSize = expandBufferSize;
		System.out.println("Expand buffer size: " + expandBufferSize);

	}
	
	public void executeExpand(String classToExpand,
			String propertyToExpand, boolean deleteExpandtedTriples,
			String uriExpansion, String uriExpansionPredicatePrefix) throws RepositoryException,
			MalformedQueryException, IOException {
		
		String queryString = "SELECT ?s ?p ?toExpand ?g WHERE {"
				+ "    GRAPH ?g {" + "    	?s a <" + classToExpand + "> ;"
				+ "      ?p ?toExpand ." + "    	FILTER(?p = <"
				+ propertyToExpand + ">).  } }";

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
		Map<String, String> registery = null;
		Map<String, String> prefixToReplace = null;

		if (uriExpansion != null && uriExpansion.equals("infer")) {
			// Identifier resolution

			File registeryFile = new File("registery.json");
			//if (!registeryFile.exists()) {
				FileUtils.copyURLToFile(new URL("http://prefix.cc/context"),
						new File("registery.json"));
			//}

			ObjectMapper mapper = new ObjectMapper();
			registery = new HashMap<String, String>();
			JsonNode node = mapper.readTree(registeryFile);
			JsonNode context = node.get("@context");
			Iterator<Map.Entry<String, JsonNode>> iter = context.fields();

			int regCount = 0;
			while (iter.hasNext()) {
				Map.Entry<String, JsonNode> entry = iter.next();
				registery.put(entry.getKey(), entry.getValue().textValue());
				regCount++;
			}

			System.out.println("Registery build finished, total items: "
					+ regCount);

			// Some prefixes are not covered by PrefixCommons at the moment and will be added here.
			prefixToReplace = new HashMap<String, String>();
			prefixToReplace.put("keggcompound", "kegg");
			prefixToReplace.put("keggdrug", "kegg");
			prefixToReplace.put("drugbank", "drugbank");
			prefixToReplace.put("uniprotkb", "uniprot");
			prefixToReplace.put("clinicaltrials.gov", "clinicaltrials");
			prefixToReplace.put("drugsproductdatabase(dpd)", "dpd");
			prefixToReplace.put("nationaldrugcodedirectory", "ndc");
			prefixToReplace.put("therapeutictargetsdatabase", "ttd");
			prefixToReplace.put("fdadruglabelatdailymed", "dailymed");
			prefixToReplace.put("chebi:chebi", "chebi");
			prefixToReplace.put("pubchemcompound", "b2rpubchem");
		}

		int count = 0;
		int accum = 0;

		Map<String, String> availablePref = new HashMap<String, String>();

		try {
			while (selectResults.hasNext()) {
				BindingSet bindingSet = selectResults.next();

				IRI subjectIri = f.createIRI(bindingSet.getValue("s").stringValue());
				IRI predicateIri = f.createIRI(bindingSet.getValue("p").stringValue());
				String stringToExpand = bindingSet.getValue("toExpand").stringValue();
				// Use graph IRI directly from the data, if no graph URI provided
				if (!graphFromParam) {
					graphIri = f.createIRI(bindingSet.getValue("g").stringValue());
				}
				
				if (uriExpansion != null) {
					if (!uriExpansion.equals("infer")) {
						stringToExpand = uriExpansion + stringToExpand;
						bulkUpdate.add(subjectIri, predicateIri,
								f.createIRI(stringToExpand), graphIri);

					} else if (uriExpansion.equals("infer")) {

						if (stringToExpand.indexOf("(") != -1) {
							stringToExpand = stringToExpand.substring(0,
									stringToExpand.indexOf("("));
						}

						if (stringToExpand.contains(":")) {

							int p = 0;

							if (stringToExpand.contains("url")) {
								p = stringToExpand.indexOf(":");
							} else {
								p = stringToExpand.lastIndexOf(":");
							}

							String prefix = stringToExpand.substring(0, p)
									.toLowerCase().replace(" ", "").trim();
							String id = stringToExpand.substring(p + 1);

							availablePref.put(prefix, "");

							if (prefixToReplace.containsKey(prefix)) {
								prefix = prefixToReplace.get(prefix);
							}

							if (registery.containsKey(prefix)) {

								stringToExpand = registery.get(prefix) + id;
								
								if(uriExpansionPredicatePrefix != null) {
									
									predicateIri = f.createIRI(uriExpansionPredicatePrefix
											+ "x-" + prefix);	
								}else {
									predicateIri = f.createIRI(propertyToExpand
											.substring(0, propertyToExpand
													.lastIndexOf("/") + 1)
											+ "x-" + prefix);
								}
								
								bulkUpdate.add(subjectIri, predicateIri,
										f.createIRI(stringToExpand),
										graphIri);
							} else {
								
								if(uriExpansionPredicatePrefix != null) {
									predicateIri = f.createIRI(uriExpansionPredicatePrefix
											+ "x-ref");
								}else {	
									predicateIri = f.createIRI(propertyToExpand
											.substring(0, propertyToExpand
													.lastIndexOf("/") + 1)
											+ "x-ref");
								}
								
								bulkUpdate.add(subjectIri, predicateIri,
										f.createLiteral(stringToExpand),
										graphIri);
							}
							
						} else {
							bulkUpdate.add(subjectIri, predicateIri,
									f.createLiteral(stringToExpand),
									graphIri);
						} // if(stringToExpand.contains(":"))
					} // if(!uriExpansion.equals("infer"))
					
				} else {
					bulkUpdate.add(subjectIri, predicateIri,
							f.createLiteral(stringToExpand), graphIri);
				} // if(uriExpansion != null)
				count++;

				
				if ((count > expandBufferSize)) {
					conn.add(bulkUpdate, graphIri);
					bulkUpdate = builder.build();

					accum += count;
					System.out.println("Updated triples: " + accum);
					count = 0;

				} else if ((count <= expandBufferSize) && !selectResults.hasNext()) {
					conn.add(bulkUpdate, graphIri);
					accum += count;
					System.out.println("Total updated triples: " + accum);
				}
			} // while results
			
			// TODO: print the content of the cross references available in the dataset, Michel asked for it.
			
			// Iterator it = availablePref.entrySet().iterator();
			// while (it.hasNext()) {
			// Map.Entry pair = (Map.Entry)it.next();
			// System.out.println(pair.getKey());
			// it.remove(); // avoids a ConcurrentModificationException
			// }

		} finally {
			selectResults.close();
			if (deleteExpandtedTriples) {
				String deleteQueryString = "DELETE { " + "GRAPH ?g {"
						+ "?s ?p ?o." + "} " + "}WHERE {" + "GRAPH ?g {"
						+ "?s a <" + classToExpand + "> ;" + "?p ?o ."
						+ "FILTER(?p = <" + propertyToExpand + ">). } } ";

				System.out.println();
				System.out.println(deleteQueryString);

				Update update = conn.prepareUpdate(deleteQueryString);
				update.execute();
			}
			conn.close();
		}
		
	
	}


}
