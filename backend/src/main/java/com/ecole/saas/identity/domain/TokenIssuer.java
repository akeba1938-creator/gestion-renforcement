package com.ecole.saas.identity.domain;

import java.util.Set;

public interface TokenIssuer {

    IssuedToken issueAccessToken(AccountRef account, Set<String> roles);

    IssuedToken issueMfaPendingToken(AccountRef account);

    record IssuedToken(String value, long expiresInSeconds) {
    }
}
