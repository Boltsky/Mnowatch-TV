import { MediaItem } from '../types/index';

export function normalizeVj(vjName: string | null | undefined): string {
  const clean = (vjName || '').toLowerCase().replace(/^(?:vj\s*|vj-)/i, '').replace(/[^a-z0-9]/g, '').trim();
  if (!clean) return '';
  if (clean.includes('kisuule') || clean.includes('kiwule') || clean.includes('kiwuule')) return 'kisuule';
  if (clean.includes('icep') || clean.includes('ice p')) return 'icep';
  if (clean === 'paxwo' || clean === 'paxwoo') return 'paxwo';
  if (clean === 'neil' || clean === 'niel') return 'neil';
  if (clean === 'uli' || clean === 'ulio') return 'ulio';
  if (clean === 'ronage' || clean === 'ronnage') return 'ronage';
  if (clean === 'freddie' || clean === 'fredy') return 'freddie';
  if (clean === 'dan' || clean === 'dandee' || clean === 'dande') return 'dan';
  if (clean === 'heavyk' || clean === 'heavyq') return 'heavyq';
  if (clean === 'hashim' || clean === 'hashimj' || clean === 'hash' || clean === 'ashim') return 'hashim';
  if (clean === 'ismak' || clean === 'ismapro') return 'isma';
  if (clean === 'aron' || clean === 'aaron') return 'aron';
  if (clean === 'raj' || clean === 'raji') return 'raj';
  if (clean === 'kam' || clean === 'kamran') return 'kamran';
  if (clean === 'charly' || clean === 'charry') return 'charly';
  if (clean === 'cabs' || clean === 'cb') return 'cabs';
  if (clean.includes('shaokhan') || clean.includes('khan')) return 'khan';
  if (clean === 'unclet' || clean === 'uncle t') return 'unclet';
  if (clean === 'martink' || clean === 'martin k') return 'martink';
  return clean;
}

export function isVjMatch(vj1: string | null | undefined, vj2: string | null | undefined): boolean {
  if (!vj1 || !vj2) return false;
  const n1 = normalizeVj(vj1);
  const n2 = normalizeVj(vj2);
  if (!n1 || !n2) return false;
  return n1 === n2 || n1.includes(n2) || n2.includes(n1);
}

export function filterByVj(items: MediaItem[], vj: string | null | undefined): MediaItem[] {
  if (!vj) return items;
  return items.filter(item => isVjMatch(item.vj, vj));
}
