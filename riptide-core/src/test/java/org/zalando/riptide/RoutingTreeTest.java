package org.zalando.riptide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.Series.SUCCESSFUL;
import static org.zalando.riptide.Binding.create;
import static org.zalando.riptide.Bindings.anySeries;
import static org.zalando.riptide.Bindings.anyStatus;
import static org.zalando.riptide.Bindings.on;
import static org.zalando.riptide.Navigators.series;
import static org.zalando.riptide.Navigators.status;
import static org.zalando.riptide.PassRoute.pass;
import static org.zalando.riptide.RoutingTree.dispatch;

@ExtendWith(MockitoExtension.class)
final class RoutingTreeTest {

    @Mock(answer = CALLS_REAL_METHODS)
    private Route other;

    @Mock(answer = CALLS_REAL_METHODS)
    private Route expected;

    private final MessageReader reader = mock(MessageReader.class);

    @Test
    void shouldExposeNavigator() {
        final RoutingTree<HttpStatus> unit = dispatch(status());

        assertThat(unit.getNavigator(), is(status()));
    }

    @Test
    void shouldUsedAttributeRoute() throws Exception {
        dispatch(status(),
                on(OK).call(expected),
                anyStatus().call(other))
                .execute(response(OK), reader);

        verify(expected).execute(any(), any());
    }

    @Test
    void shouldUsedWildcardRoute() throws Exception {
        dispatch(status(),
                on(OK).call(other),
                anyStatus().call(expected))
                .execute(response(CREATED), reader);

        verify(expected).execute(any(), any());
    }

    @Test
    void shouldUsedAddedAttributeRoute() throws Exception {
        dispatch(status(),
                anyStatus().call(other))
                .merge(on(OK).call(expected))
                .execute(response(OK), reader);

        verify(expected).execute(any(), any());
    }

    @Test
    void shouldUsedAddedWildcardRoute() throws Exception {
        dispatch(status(),
                on(OK).call(other))
                .merge(anyStatus().call(expected))
                .execute(response(CREATED), reader);

        verify(expected).execute(any(), any());
    }

    @Test
    void shouldUseLastWildcardRoute() throws Exception {
        final RoutingTree<HttpStatus> merge = dispatch(status(),
                anyStatus().call(other))
                .merge(anyStatus().call(expected));

        merge.execute(response(OK), reader);

        verify(expected).execute(any(), any());
    }

    @Test
    void shouldUseLastAttributeRoute() throws Exception {
        dispatch(status(),
                on(OK).call(other))
                .merge(on(OK).call(expected))
                .execute(response(OK), reader);

        verify(expected).execute(any(), any());
    }

    @Test
    void shouldUseLastAddedAttributeRoute() throws Exception {
        dispatch(status(),
                on(OK).call(other),
                anyStatus().call(other))
                .merge(on(OK).call(other))
                .merge(on(OK).call(expected))
                .execute(response(OK), reader);

        verify(expected).execute(any(), any());
    }

    @Test
    void shouldUseLastAddedWildcardRoute() throws Exception {
        dispatch(status(),
                on(OK).call(other),
                anyStatus().call(other))
                .merge(singletonList(anyStatus().call(expected)))
                .execute(response(CREATED), reader);

        verify(expected).execute(any(), any());
    }

    @Test
    void shouldMergeRecursively() throws Exception {
        final RoutingTree<Series> left = dispatch(series(),
                on(SUCCESSFUL).dispatch(status(),
                        anyStatus().call(other)),
                anySeries().call(other));

        final RoutingTree<Series> right = dispatch(series(),
                on(SUCCESSFUL).dispatch(status(),
                        on(CREATED).call(expected)));

        left.merge(right).execute(response(CREATED), reader);

        verify(expected).execute(any(), any());
    }

    @Test
    void shouldUseOtherWhenMergingOnDifferentAttributes() throws Exception {
        final RoutingTree<Series> left = dispatch(series(),
                on(SUCCESSFUL).dispatch(status(),
                        anyStatus().call(other)),
                anySeries().call(pass()));

        final RoutingTree<HttpStatus> right = dispatch(status(),
                on(CREATED).call(expected),
                anyStatus().call(pass()));

        final Route merge = left.merge(right);

        merge.execute(response(CREATED), reader);
        merge.execute(response(OK), reader);

        verify(expected).execute(any(), any());
        verify(other, never()).execute(any(), any());
    }

    @Test
    void shouldUseNonRoutingTreeOnMerge() throws Exception {
        final RoutingTree<Series> left = dispatch(series(),
                on(SUCCESSFUL).dispatch(status(),
                        anyStatus().call(other)),
                anySeries().call(pass()));

        final Route merge = left.merge(expected);

        merge.execute(response(CREATED), reader);
        merge.execute(response(OK), reader);

        verify(expected, times(2)).execute(any(), any());
        verify(other, never()).execute(any(), any());
    }

    @Test
    void shouldCreateNewRoutingTreeIfChanged() {
        final RoutingTree<HttpStatus> tree = dispatch(status(), on(OK).call(pass()));
        final RoutingTree<HttpStatus> result = tree.merge(anyStatus().call(pass()));
        assertNotEquals(tree, result);
    }

    @Test
    void shouldCreateNewRoutingTreeIfNotChanged() {
        final RoutingTree<HttpStatus> tree = dispatch(status(), on(OK).call(pass()));
        final RoutingTree<HttpStatus> result = tree.merge(on(OK).call(pass()));
        assertNotEquals(tree, result);
    }

    @Test
    void shouldCatchIOExceptionFromResponse() throws Exception {
        final ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getStatusCode()).thenThrow(new IOException());

        final RoutingTree<HttpStatus> tree = dispatch(status(),
                singletonList(anyStatus().call(pass())));

        assertThrows(IOException.class, () ->
                tree.execute(response, reader));
    }

    @Test
    void shouldCatchIOExceptionFromBinding() throws Exception {
        final HttpStatus anyStatus = null;
        final Binding<HttpStatus> binding = create(anyStatus, (response, converters) -> {
            throw new IOException();
        });

        final ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getStatusCode()).thenReturn(OK);

        final RoutingTree<HttpStatus> tree = dispatch(status(), singletonList(binding));

        assertThrows(IOException.class, () ->
                tree.execute(response, reader));
    }

    @Test
    void shouldFailForDuplicateBindings() {
        assertThrows(IllegalArgumentException.class, () ->
                dispatch(status(),
                        on(OK).call(pass()),
                        on(OK).call(pass())));
    }

    private MockClientHttpResponse response(final HttpStatus status) {
        return new MockClientHttpResponse(new byte[0], status);
    }

}
