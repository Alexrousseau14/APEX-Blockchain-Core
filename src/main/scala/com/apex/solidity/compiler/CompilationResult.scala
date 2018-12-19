package com.apex.solidity.compiler


import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util
import java.util.{ArrayList, Collections, List, Map}

import com.fasterxml.jackson.annotation.{JsonIgnore, JsonIgnoreProperties, JsonProperty}
import com.fasterxml.jackson.databind.ObjectMapper

import scala.collection.mutable

@JsonIgnoreProperties(ignoreUnknown = true) object CompilationResult {
  @JsonIgnore
  @throws[IOException]
  def parse(rawJson: String): CompilationResult = if (rawJson == null || rawJson.isEmpty) {
    val empty = new CompilationResult
    //empty.contracts = Collections.emptyMap
    empty.version = ""
    empty
  }
  else new ObjectMapper().readValue(rawJson, classOf[CompilationResult])

  @JsonIgnoreProperties(ignoreUnknown = true) class ContractMetadata {
    var abi: String = null
    var bin: String = null
    var solInterface: String = null
    var metadata: String = null

    def getInterface: String = solInterface

    def setInterface(solInterface: String): Unit = {
      this.solInterface = solInterface
    }
  }
}

@JsonIgnoreProperties(ignoreUnknown = true) class CompilationResult {
  @JsonProperty("contracts") private var contracts = mutable.Map.empty[String, CompilationResult.ContractMetadata]
  @JsonProperty("version") var version: String = null

  /**
    * @return the contract's path given this compilation result contains exactly one contract
    */
  @JsonIgnore def getContractPath: Path = {
    if (contracts.size > 1)
      throw new UnsupportedOperationException("Source contains more than 1 contact. Please specify the contract name. Available keys (" + getContractKeys + ").")
    else {
      val key: String = contracts.keySet.iterator.next
      Paths.get(key.substring(0, key.lastIndexOf(':')))
    }
  }

  /**
    * @return the contract's name given this compilation result contains exactly one contract
    */
  @JsonIgnore def getContractName: String = {
    if (contracts.size > 1)
      throw new UnsupportedOperationException("Source contains more than 1 contact. Please specify the contract name. Available keys (" + getContractKeys + ").")
    else {
      val key : String = contracts.keySet.iterator.next
      key.substring(key.lastIndexOf(':') + 1)
    }
  }

  /**
    * @param contractName The contract name
    * @return the first contract found for a given contract name; use { @link #getContract(Path, String)} if this compilation result contains more than one contract with the same name
    */
  @JsonIgnore def getContract(contractName: String): CompilationResult.ContractMetadata = {
    if (contractName == null && contracts.size == 1)
      return contracts.values.iterator.next
    else if (contractName == null || contractName.isEmpty)
      throw new UnsupportedOperationException("Source contains more than 1 contact. Please specify the contract name. Available keys (" + getContractKeys + ").")
    for (entry <- contracts) {
      val key: String = entry._1
      val name = key.substring(key.lastIndexOf(':') + 1)
      if (contractName == name)
        return entry._2
    }
    throw new UnsupportedOperationException("No contract found with name '" + contractName + "'. Please specify a valid contract name. Available keys (" + getContractKeys + ").")
  }

  /**
    * @param contractPath The contract path
    * @param contractName The contract name
    * @return the contract with key { @code contractPath:contractName} if it exists; { @code null} otherwise
    */
  @JsonIgnore def getContract(contractPath: Path, contractName: String): CompilationResult.ContractMetadata = {
    contracts.get(contractPath.toAbsolutePath.toString + ':' + contractName).get
  }

  /**
    * @return all contracts from this compilation result
    */
//  @JsonIgnore def getContracts: util.List[CompilationResult.ContractMetadata] = {
//    new util.ArrayList[CompilationResult.ContractMetadata](contracts.values)
//  }

  /**
    * @return all keys from this compilation result
    */
  @JsonIgnore def getContractKeys = {
    //new util.ArrayList[String](contracts.keySet)
    contracts.keySet
  }
}
