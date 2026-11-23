package edu.university.portal.service;

import edu.university.portal.dto.OrcidWork;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StubOrcidWorksService implements OrcidWorksService {

    @Override
    public List<OrcidWork> findWorks(String orcidId) {
        // TODO Phase 3: call https://pub.orcid.org/v3.0/{orcid}/works with real ORCID credentials.
        return List.of(
                new OrcidWork(
                        "Sample ORCID work for " + orcidId,
                        List.of("Sample Author"),
                        "Journal of University Research",
                        2024,
                        "10.5555/orcid.sample." + orcidId.replace("-", "").toLowerCase(),
                        orcidId
                ),
                new OrcidWork(
                        "A second sample work returned until ORCID credentials are configured",
                        List.of("Sample Author", "Co Author"),
                        "Proceedings of the Research Office",
                        2022,
                        null,
                        orcidId
                )
        );
    }
}
