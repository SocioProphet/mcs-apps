import {KgNode} from "./models/kg/KgNode";
import {KgEdge} from "./models/kg/KgEdge";
import {KgPath} from "./models/kg/KgPath";
import {Benchmark} from "./models/benchmark/Benchmark";
import {BenchmarkSubmission} from "./models/benchmark/BenchmarkSubmission";

export class TestData {
  static readonly kgId = "cskg";

  static readonly datasources = ["portal_test_data"];

  static get benchmarks(): Cypress.Chainable<Benchmark[]> {
    return cy.fixture("benchmark/benchmarks.json");
  }

  static get benchmarkSubmissions(): Cypress.Chainable<BenchmarkSubmission[]> {
    return cy.fixture("benchmark/benchmark_submissions.json");
  }

  static get nodes(): Cypress.Chainable<KgNode[]> {
    return cy.fixture("kg/nodes.json");
  }

  static get edges(): Cypress.Chainable<KgEdge[]> {
    return cy.fixture("kg/edges.json");
  }

  static get paths(): Cypress.Chainable<KgPath[]> {
    return cy.fixture("kg/paths.json");
  }
}
