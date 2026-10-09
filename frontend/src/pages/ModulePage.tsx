import {useParams} from 'react-router-dom'
export default function ModulePage(){const {module=''}=useParams();const title=module.replaceAll('-',' ').replace(/\b\w/g,c=>c.toUpperCase());return <div className="empty"><p className="eyebrow">WORKSPACE MODULE</p><h1>{title}<span>.</span></h1><p>This module will connect to the IndexX database as the project grows.</p></div>}
