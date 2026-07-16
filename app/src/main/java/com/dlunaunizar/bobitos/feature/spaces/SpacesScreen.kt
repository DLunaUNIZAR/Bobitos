package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceSummary

@Composable
fun SpacesScreen(
    state: UiState<List<SpaceSummary>>,
    onSpaceSelected: (SpaceSummary) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        UiState.Loading -> LoadingContent(modifier)
        is UiState.Error -> ErrorContent(modifier, state.message)
        is UiState.Content -> SpacesContent(
            spaces = state.value,
            onSpaceSelected = onSpaceSelected,
            onProfileClick = onProfileClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun SpacesContent(
    spaces: List<SpaceSummary>,
    onSpaceSelected: (SpaceSummary) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.spaces_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            TextButton(onClick = onProfileClick) {
                Text(text = stringResource(R.string.profile_open))
            }
        }
        Text(
            text = stringResource(R.string.spaces_description),
            style = MaterialTheme.typography.bodyLarge,
        )

        if (spaces.isEmpty()) {
            Text(text = stringResource(R.string.spaces_empty))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(
                    items = spaces,
                    key = SpaceSummary::id,
                ) { space ->
                    SpaceCard(
                        space = space,
                        onClick = { onSpaceSelected(space) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpaceCard(
    space: SpaceSummary,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = space.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = pluralStringResource(
                    id = R.plurals.space_members,
                    count = space.memberCount,
                    space.memberCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.generic_loading),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ErrorContent(
    modifier: Modifier = Modifier,
    message: String?,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message ?: stringResource(R.string.generic_error))
    }
}
