package com.coresecure.brightcove.wrapper.webservices;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.day.cq.replication.ReplicationAction;

/**
 * The brightcove:// agent's authorization check (on-prem matrix row 31).
 *
 * <p>It runs on the brightcove_admin SERVICE resolver. When that user cannot read /home,
 * {@code getAuthorizable()} returns null, which must mean "not authorized" (and is why
 * ui.config.onprem grants the read). A declared group listed in allowedGroups
 * authorizes. Context: current/docs/dam-sync-on-activation.md</p>
 */
class BrcReplicationHandlerAuthorizationTest {

    private final BrcReplicationHandler handler = new BrcReplicationHandler();
    private ResourceResolver rr;
    private UserManager userManager;
    private ReplicationAction action;

    @BeforeEach
    void setUp() {
        rr = mock(ResourceResolver.class);
        userManager = mock(UserManager.class);
        when(rr.adaptTo(UserManager.class)).thenReturn(userManager);
        action = mock(ReplicationAction.class);
        when(action.getUserId()).thenReturn("author1");
    }

    @Test
    void unreadableUserIsNotAuthorized() throws Exception {
        when(userManager.getAuthorizable("author1")).thenReturn(null);
        assertFalse(handler.isAuthorized(rr, action, Collections.singletonList("administrators")),
                "a user the service resolver cannot read must not be authorized");
    }

    @Test
    void declaredGroupInAllowedGroupsAuthorizes() throws Exception {
        user(group("contributors"), group("administrators"));
        assertTrue(handler.isAuthorized(rr, action, Arrays.asList("brightcove-users", "administrators")));
    }

    @Test
    void noMatchingGroupIsNotAuthorized() throws Exception {
        // Mock-only: a user whose memberOf() lacks the listed group is not authorized. (Real
        // Oak 1.68 includes `everyone` in memberOf(), so there everyone authorizes.)
        user(group("contributors"));
        assertFalse(handler.isAuthorized(rr, action, Collections.singletonList("everyone")));
    }

    @Test
    void noUserManagerIsNotAuthorized() throws Exception {
        when(rr.adaptTo(UserManager.class)).thenReturn(null);
        assertFalse(handler.isAuthorized(rr, action, Collections.singletonList("administrators")));
    }

    private void user(Group... groups) throws Exception {
        Authorizable auth = mock(Authorizable.class);
        List<Group> list = Arrays.asList(groups);
        when(auth.memberOf()).thenAnswer(inv -> list.iterator());
        when(userManager.getAuthorizable("author1")).thenReturn(auth);
    }

    private static Group group(String id) throws Exception {
        Group g = mock(Group.class);
        when(g.getID()).thenReturn(id);
        return g;
    }
}
