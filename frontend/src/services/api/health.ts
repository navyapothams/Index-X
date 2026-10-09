import {apiClient} from './client'
import type {HealthResponse} from '../../types/api'
export async function getHealth(){return (await apiClient.get<HealthResponse>('/health')).data}
