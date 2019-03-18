# About
A project to execute [SPARQL](https://www.w3.org/TR/sparql11-query/) queries from string, URL or multiple files using [RDF4J](http://rdf4j.org/).

* The user can execute **SPARQL queries** by
  * Passing a SPARQL **query string** in `-sp` param 
  * Providing a **URL** in `-f` param
  * Providing the path to a directory where the queries are stored in `.rq` text files and executed in the **alphabetical order** of their filename. 
  * A **YAML file** with multiple queries. See the [example in resources](https://github.com/vemonet/rdf4j-sparql-operations/blob/master/src/main/resources/describe_statistics-drugbank.yaml)
* **Update**, **construct** and **select** operations supported.
* It is possible to optionally define **username** and **password** for the SPARQL endpoint.
* Examples queries: [data2services-insert](https://github.com/MaastrichtU-IDS/data2services-insert).



# Docker build
```shell
docker build -t rdf4j-sparql-operations .
```
# Docker run

### Usage

```shell
docker run -it --rm rdf4j-sparql-operations -h
```

### Select

On [DBpedia](http://dbpedia.org/sparql) using a SPARQL query as argument.

```shell
docker run -it --rm rdf4j-sparql-operations -op select \
	-sp "select distinct ?Concept where {[] a ?Concept} LIMIT 10" \
	-ep "http://dbpedia.org/sparql"
```

### Construct

On [graphdb.dumontierlab.com](http://graphdb.dumontierlab.com/) using GitHub URL to get SPARQL query.

```shell
docker run -it --rm rdf4j-sparql-operations -op construct \
	-ep "http://graphdb.dumontierlab.com/repositories/ncats-red-kg" \
	-f "https://raw.githubusercontent.com/MaastrichtU-IDS/data2services-insert/master/resources/construct-test.rq" 
```

### Update

Multiple `INSERT` on [graphdb.dumontierlab.com](http://graphdb.dumontierlab.com/).

```shell
docker run -it --rm -v "/data/data2services-insert/insert-biolink/drugbank":/data \
	rdf4j-sparql-operations -f "/data" -op update \
	-ep "http://graphdb.dumontierlab.com/repositories/test/statements" \
	-un USERNAME -pw PASSWORD
```

* GraphDB requires to add `/statements` at the end of the endpoint URL for `INSERT`

### YAML

A YAML file can be used to provide multiple ordered queries.

```shell
# Run on a YAML with construct
docker run -it --rm 
	-v "$(pwd)/rdf4j-sparql-operations/src/main/resources/describe_statistics-drugbank.yaml":/data/stats.yaml \
	sparql-rdf4j-operations -f "/data/stats.yaml" -op construct \
	-ep "http://graphdb.dumontierlab.com/repositories/ncats-red-kg" \
	-un username -pw password
```



# Set variables

Variables can be set in the SPARQL queries using a `_` at the beggining: `?_myVar`. See example:

```shell
PREFIX owl: <http://www.w3.org/2002/07/owl#>
CONSTRUCT 
{ 
    ?class a <?_classType> .
}
WHERE {
    GRAPH <?_graphUri> {
        [] a ?class .
    }
}
```

Execute with 3 variables:

```shell
docker run -it --rm -v /data/operations:/data rdf4j-sparql-operations \
	-f "/data/operations/construct.rq" -op construct \
	-ep "http://localhost:7200/repositories/test" \
    -var serviceUrl:http://localhost:7200/repositories/test graphUri:https://w3id.org/data2services/graph classType:http://test/class
```



# Examples

From [data2services-insert](https://github.com/MaastrichtU-IDS/data2services-insert), to transform generic RDF generated by [AutoR2RML](https://github.com/amalic/AutoR2RML) and [xml2rdf](https://github.com/MaastrichtU-IDS/xml2rdf) to the [BioLink](https://biolink.github.io/biolink-model/docs/) model.

```shell
# DrugBank
docker run -it --rm -v "$PWD/insert-biolink/drugbank":/data rdf4j-sparql-operations \
	-f "/data" -un USERNAME -pw PASSWORD \
	-ep "http://graphdb.dumontierlab.com/repositories/ncats-test/statements" \
	-var serviceUrl:http://localhost:7200/repositories/test inputGraph:http://data2services/graph/xml2rdf outputGraph:https://w3id.org/data2services/graph/biolink/drugbank

# HGNC
docker run -it --rm -v "$PWD/insert-biolink/hgnc":/data rdf4j-sparql-operations \
	-f "/data" -un USERNAME -pw PASSWORD \
	-ep "http://graphdb.dumontierlab.com/repositories/ncats-test/statements" \
	-var serviceUrl:http://localhost:7200/repositories/test inputGraph:http://data2services/graph/autor2rml outputGraph:https://w3id.org/data2services/graph/biolink/hgnc
```

