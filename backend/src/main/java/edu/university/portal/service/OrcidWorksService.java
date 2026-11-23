package edu.university.portal.service;

import edu.university.portal.dto.OrcidWork;

import java.util.List;

/**
 * Phase 3 replaces {@link StubOrcidWorksService} with a client that calls
 * the ORCID API using real credentials. Do not hard-code secrets here.
 */
public interface OrcidWorksService {

    List<OrcidWork> findWorks(String orcidId);
}
