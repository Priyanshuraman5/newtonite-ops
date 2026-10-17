# Engineering decisions
(Rewrite each in your own words before submitting: context, options, choice, trade-off.)
1. **Conditional UPDATE for claim** vs SELECT-then-UPDATE or row locks: one atomic statement, loser gets 0 rows -> 409.
2. **Version column (optimistic locking)** vs pessimistic locks: no locks held while a user edits; conflict returns latest item.
3. **Idempotency via unique (user, key) row** inside the create transaction: concurrent retries block, then replay the stored item.
4. **One Permissions.require()** scoped to the item's team; outsiders get 404 so existence isn't leaked.
5. **History in the same transaction** as each change, keyset pagination + partial/GIN indexes for scale.
Not built: frontend, outbox worker, websockets, email.
